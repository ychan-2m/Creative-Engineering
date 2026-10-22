// WebSocket/STOMP 연결 관리. E07: 끊기면 1초, 2초, 4초 간격으로 최대 5회 재연결을 시도한다.
window.WebTalkSocket = (() => {
  const RECONNECT_DELAYS_MS = [1000, 2000, 4000, 4000, 4000];

  let client = null;
  let reconnectAttempt = 0;
  let manualDisconnect = true;
  let reconnectTimer = null;

  let currentRoomId = null;
  let roomMessageHandler = null;
  let roomSubscription = null;
  let errorSubscription = null;
  let roomsSubscription = null;
  let subscriptionRevision = 0;

  let onStatusChange = () => {};
  let onRoomError = () => {};
  let onRoomsChanged = () => {};
  let onRoomReady = () => {};

  function buildClient() {
    const protocol = location.protocol === 'https:' ? 'wss' : 'ws';
    return new StompJs.Client({
      brokerURL: `${protocol}://${location.host}/ws`,
      connectionTimeout: 10000,
      reconnectDelay: 0, // 자동 재연결 대신 아래에서 직접 간격을 관리한다.
      onConnect: handleConnect,
      onWebSocketClose: handleClose,
      onStompError: (frame) => {
        console.error('STOMP 오류', frame.headers, frame.body);
      },
    });
  }

  function handleConnect() {
    reconnectAttempt = 0;
    roomSubscription = null;
    errorSubscription = null;
    roomsSubscription = null;

    errorSubscription = client.subscribe('/user/queue/errors', (message) => {
      try {
        onRoomError(JSON.parse(message.body));
      } catch (e) {
        console.error('오류 메시지 파싱 실패', e);
      }
    });

    roomsSubscription = client.subscribe('/user/queue/rooms', () => onRoomsChanged());
    if (currentRoomId != null && roomMessageHandler) {
      subscribeRoomInternal(currentRoomId, roomMessageHandler);
    }
    onStatusChange('connected');
  }

  function handleClose() {
    subscriptionRevision++;
    roomSubscription = null;
    errorSubscription = null;
    roomsSubscription = null;
    clearTimeout(reconnectTimer);
    if (manualDisconnect) {
      return;
    }
    if (reconnectAttempt >= RECONNECT_DELAYS_MS.length) {
      onStatusChange('disconnected');
      return;
    }
    const attemptNumber = reconnectAttempt + 1;
    onStatusChange('reconnecting', attemptNumber, RECONNECT_DELAYS_MS.length);
    const delay = RECONNECT_DELAYS_MS[reconnectAttempt];
    reconnectAttempt += 1;
    reconnectTimer = setTimeout(() => {
      if (!manualDisconnect) {
        reconnectTimer = null;
        restartClient();
      }
    }, delay);
  }

  async function restartClient() {
    const target = client;
    if (!target) return;
    // reconnectDelay=0일 때 닫힌 소켓의 Client는 ACTIVE 상태로 남을 수 있다.
    // activate()가 무시되지 않도록 먼저 INACTIVE 상태로 전환한다.
    await target.deactivate();
    if (!manualDisconnect && target === client) target.activate();
  }

  function subscribeRoomInternal(roomId, onMessage) {
    if (roomSubscription) {
      roomSubscription.unsubscribe();
      roomSubscription = null;
    }
    const revision = ++subscriptionRevision;
    const receipt = `room-${revision}`;
    client.watchForReceipt(receipt, () => {
      if (revision === subscriptionRevision && currentRoomId === roomId) onRoomReady();
    });
    roomSubscription = client.subscribe(`/topic/chat/${roomId}`, (message) => {
      if (revision === subscriptionRevision && currentRoomId === roomId) onMessage(JSON.parse(message.body));
    }, { receipt });
  }

  function connect(handlers) {
    onStatusChange = handlers.onStatusChange || onStatusChange;
    onRoomError = handlers.onRoomError || onRoomError;
    onRoomsChanged = handlers.onRoomsChanged || onRoomsChanged;
    onRoomReady = handlers.onRoomReady || onRoomReady;

    manualDisconnect = false;
    reconnectAttempt = 0;
    onStatusChange('connecting');

    client = buildClient();
    client.activate();
  }

  function disconnect() {
    manualDisconnect = true;
    if (reconnectTimer) {
      clearTimeout(reconnectTimer);
      reconnectTimer = null;
    }
    if (client) {
      client.deactivate();
    }
    currentRoomId = null;
    roomMessageHandler = null;
    roomSubscription = null;
    errorSubscription = null;
    roomsSubscription = null;
    subscriptionRevision++;
  }

  function retryNow() {
    clearTimeout(reconnectTimer);
    reconnectTimer = null;
    reconnectAttempt = 0;
    manualDisconnect = false;
    onStatusChange('connecting');
    restartClient();
  }

  function subscribeRoom(roomId, onMessage) {
    currentRoomId = roomId;
    roomMessageHandler = onMessage;
    if (client && client.connected) {
      subscribeRoomInternal(roomId, onMessage);
    }
  }

  function unsubscribeRoom() {
    subscriptionRevision++;
    if (client?.connected && roomSubscription) roomSubscription.unsubscribe();
    roomSubscription = null;
    currentRoomId = null;
    roomMessageHandler = null;
  }

  function publish(roomId, clientMessageId, content) {
    if (!client || !client.connected) {
      return false;
    }
    try {
      client.publish({
        destination: '/app/chat.send',
        body: JSON.stringify({ roomId, clientMessageId, content }),
      });
      return true;
    } catch (error) {
      console.error('메시지 전송 실패', error);
      return false;
    }
  }

  function isConnected() {
    return !!(client && client.connected);
  }

  return { connect, disconnect, subscribeRoom, unsubscribeRoom, publish, isConnected, retryNow };
})();
