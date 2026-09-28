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

  let onStatusChange = () => {};
  let onRoomError = () => {};

  function buildClient() {
    const protocol = location.protocol === 'https:' ? 'wss' : 'ws';
    return new StompJs.Client({
      brokerURL: `${protocol}://${location.host}/ws`,
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
    onStatusChange('connected');

    errorSubscription = client.subscribe('/user/queue/errors', (message) => {
      try {
        onRoomError(JSON.parse(message.body));
      } catch (e) {
        console.error('오류 메시지 파싱 실패', e);
      }
    });

    if (currentRoomId != null && roomMessageHandler) {
      subscribeRoomInternal(currentRoomId, roomMessageHandler);
    }
  }

  function handleClose() {
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
        client.activate();
      }
    }, delay);
  }

  function subscribeRoomInternal(roomId, onMessage) {
    if (roomSubscription) {
      roomSubscription.unsubscribe();
      roomSubscription = null;
    }
    roomSubscription = client.subscribe(`/topic/chat/${roomId}`, (message) => {
      onMessage(JSON.parse(message.body));
    });
  }

  function connect(handlers) {
    onStatusChange = handlers.onStatusChange || onStatusChange;
    onRoomError = handlers.onRoomError || onRoomError;

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
  }

  function retryNow() {
    reconnectAttempt = 0;
    manualDisconnect = false;
    onStatusChange('connecting');
    if (client) {
      client.activate();
    }
  }

  function subscribeRoom(roomId, onMessage) {
    currentRoomId = roomId;
    roomMessageHandler = onMessage;
    if (client && client.connected) {
      subscribeRoomInternal(roomId, onMessage);
    }
  }

  function publish(roomId, clientMessageId, content) {
    if (!client || !client.connected) {
      return false;
    }
    client.publish({
      destination: '/app/chat.send',
      body: JSON.stringify({ roomId, clientMessageId, content }),
    });
    return true;
  }

  function isConnected() {
    return !!(client && client.connected);
  }

  return { connect, disconnect, subscribeRoom, publish, isConnected, retryNow };
})();
