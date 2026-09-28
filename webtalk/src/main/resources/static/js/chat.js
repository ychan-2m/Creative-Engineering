// 대화 목록/검색/채팅방/실시간 송수신을 모두 담당하는 메인 화면 스크립트.
(() => {
  const SEND_TIMEOUT_MS = 5000; // 이 시간 안에 서버 확인(같은 clientMessageId의 브로드캐스트)이 없으면 실패 처리한다.

  let currentUser = null;       // { userId, loginId, nickname }
  let currentRoom = null;       // { roomId, peer: { userId, nickname } }
  let latestConnectionStatus = 'connecting';
  const pendingTimers = new Map(); // clientMessageId -> timeout handle

  // ---------- DOM 참조 ----------
  const currentUserLabel = document.getElementById('currentUserLabel');
  const logoutButton = document.getElementById('logoutButton');

  const searchInput = document.getElementById('userSearchInput');
  const searchButton = document.getElementById('userSearchButton');
  const searchResults = document.getElementById('searchResults');
  const searchEmpty = document.getElementById('searchEmpty');

  const roomListEl = document.getElementById('roomList');

  const chatBody = document.getElementById('chatBody');
  const chatPlaceholder = document.getElementById('chatPlaceholder');
  const chatRoomView = document.getElementById('chatRoomView');
  const peerNameLabel = document.getElementById('peerNameLabel');
  const connectionStatusEl = document.getElementById('connectionStatus');
  const messageListEl = document.getElementById('messageList');
  const messageInput = document.getElementById('messageInput');
  const sendButton = document.getElementById('sendButton');
  const backButton = document.getElementById('backButton');

  // ---------- 초기화 ----------
  async function init() {
    try {
      currentUser = await WebTalkApi.get('/api/me');
    } catch (err) {
      location.href = '/login.html';
      return;
    }
    currentUserLabel.textContent = `${currentUser.nickname} (${currentUser.loginId})`;

    await loadRoomList();

    WebTalkSocket.connect({
      onStatusChange: handleConnectionStatusChange,
      onRoomError: handleRoomError,
    });
  }

  logoutButton.addEventListener('click', async () => {
    WebTalkSocket.disconnect();
    try {
      await WebTalkApi.post('/api/auth/logout');
    } finally {
      location.href = '/login.html';
    }
  });

  // ---------- 사용자 검색 (FR04) ----------
  async function runSearch() {
    const query = searchInput.value.trim();
    searchResults.innerHTML = '';
    searchEmpty.hidden = true;
    if (!query) {
      return;
    }
    const results = await WebTalkApi.get(`/api/users?query=${encodeURIComponent(query)}`);
    if (results.length === 0) {
      searchEmpty.hidden = false;
      return;
    }
    results.forEach((user) => {
      const li = document.createElement('li');
      li.textContent = `${user.nickname} (${user.loginId})`;
      li.addEventListener('click', () => openDirectRoomWith(user));
      searchResults.appendChild(li);
    });
  }

  searchButton.addEventListener('click', runSearch);
  searchInput.addEventListener('keydown', (event) => {
    if (event.key === 'Enter') {
      event.preventDefault();
      runSearch();
    }
  });

  async function openDirectRoomWith(user) {
    const room = await WebTalkApi.post('/api/chat-rooms/direct', { targetUserId: user.userId });
    searchResults.innerHTML = '';
    searchInput.value = '';
    searchEmpty.hidden = true;
    await loadRoomList();
    openRoom(room.roomId, room.peer);
  }

  // ---------- 대화 목록 (FR05 목록 조회) ----------
  async function loadRoomList() {
    const rooms = await WebTalkApi.get('/api/chat-rooms');
    roomListEl.innerHTML = '';

    if (rooms.length === 0) {
      const empty = document.createElement('li');
      empty.className = 'empty';
      empty.textContent = '아직 대화방이 없습니다. 위에서 사용자를 검색해 대화를 시작하세요.';
      roomListEl.appendChild(empty);
      return;
    }

    rooms.forEach((room) => {
      const li = document.createElement('li');
      li.dataset.roomId = room.roomId;
      if (currentRoom && currentRoom.roomId === room.roomId) {
        li.classList.add('active');
      }

      const meta = document.createElement('div');
      meta.className = 'room-meta';
      const name = document.createElement('span');
      name.className = 'peer-name';
      name.textContent = room.peer.nickname;
      meta.appendChild(name);
      if (room.unreadCount > 0) {
        const badge = document.createElement('span');
        badge.className = 'unread-badge';
        badge.textContent = room.unreadCount > 99 ? '99+' : String(room.unreadCount);
        meta.appendChild(badge);
      }
      li.appendChild(meta);

      const preview = document.createElement('div');
      preview.className = 'last-message';
      preview.textContent = room.lastMessageContent || '아직 메시지가 없습니다.';
      li.appendChild(preview);

      li.addEventListener('click', () => openRoom(room.roomId, room.peer));
      roomListEl.appendChild(li);
    });
  }

  function markActiveRoomInList(roomId) {
    Array.from(roomListEl.children).forEach((li) => {
      li.classList.toggle('active', Number(li.dataset.roomId) === roomId);
    });
  }

  // ---------- 채팅방 열기 (FR07 대화 기록 조회) ----------
  async function openRoom(roomId, peer) {
    currentRoom = { roomId, peer };
    peerNameLabel.textContent = peer.nickname;
    messageListEl.innerHTML = '';

    chatPlaceholder.hidden = true;
    chatRoomView.hidden = false;
    chatBody.classList.add('room-open');
    markActiveRoomInList(roomId);
    applyConnectionStatusUI(latestConnectionStatus);

    WebTalkSocket.subscribeRoom(roomId, handleIncomingMessage);

    try {
      const history = await WebTalkApi.get(`/api/chat-rooms/${roomId}/messages`);
      history.forEach((message) => appendBubble(buildBubbleModel(message, 'sent')));
      scrollMessagesToBottom();
    } catch (err) {
      if (err.status === 403) {
        // E06: 권한 없는 방이면 목록으로 돌려보낸다.
        alert('이 대화방에 접근할 수 없습니다.');
        closeRoom();
        await loadRoomList();
      }
    }
  }

  function closeRoom() {
    currentRoom = null;
    chatBody.classList.remove('room-open');
    chatPlaceholder.hidden = false;
    chatRoomView.hidden = true;
  }

  backButton.addEventListener('click', () => {
    chatBody.classList.remove('room-open');
  });

  // ---------- 메시지 렌더링 ----------
  function buildBubbleModel(message, status) {
    return {
      clientMessageId: message.clientMessageId,
      mine: message.senderId === currentUser.userId,
      content: message.content,
      createdAt: message.createdAt,
      status,
    };
  }

  function formatTime(iso) {
    try {
      return new Date(iso).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' });
    } catch (e) {
      return '';
    }
  }

  function appendBubble(model) {
    const row = document.createElement('div');
    row.className = 'bubble-row' + (model.mine ? ' mine' : '');
    if (model.clientMessageId) {
      row.dataset.clientId = model.clientMessageId;
    }
    row.dataset.content = model.content;

    const bubble = document.createElement('div');
    bubble.className = 'bubble state-' + model.status;

    if (!model.mine) {
      const sender = document.createElement('div');
      sender.className = 'sender';
      sender.textContent = currentRoom.peer.nickname;
      bubble.appendChild(sender);
    }

    const contentEl = document.createElement('div');
    contentEl.className = 'content';
    contentEl.textContent = model.content; // innerHTML 미사용 (5.8 스크립트 삽입 대응)
    bubble.appendChild(contentEl);

    const meta = document.createElement('div');
    meta.className = 'meta';
    meta.appendChild(buildStatusNode(model));
    bubble.appendChild(meta);

    row.appendChild(bubble);
    messageListEl.appendChild(row);
    return row;
  }

  function buildStatusNode(model) {
    const span = document.createElement('span');
    span.className = 'state-text';
    if (model.status === 'sending') {
      span.textContent = '전송 중';
    } else if (model.status === 'failed') {
      span.textContent = '전송 실패';
    } else {
      span.textContent = model.createdAt ? formatTime(model.createdAt) : '전송됨';
    }
    return span;
  }

  function findBubbleRow(clientMessageId) {
    return messageListEl.querySelector(`.bubble-row[data-client-id="${CSS.escape(clientMessageId)}"]`);
  }

  function setBubbleStatus(row, status, createdAt) {
    const bubble = row.querySelector('.bubble');
    bubble.className = 'bubble state-' + status;
    const meta = bubble.querySelector('.meta');
    meta.innerHTML = '';
    meta.appendChild(buildStatusNode({ status, createdAt }));

    if (status === 'failed') {
      const retryButton = document.createElement('button');
      retryButton.type = 'button';
      retryButton.className = 'retry-button';
      retryButton.textContent = '재시도';
      retryButton.addEventListener('click', () => retrySend(row));
      meta.appendChild(retryButton);
    }
  }

  function scrollMessagesToBottom() {
    messageListEl.scrollTop = messageListEl.scrollHeight;
  }

  // ---------- 전송 (FR06, 4.4 메시지 상태 규칙) ----------
  function updateSendButtonState() {
    sendButton.disabled = messageInput.value.trim().length === 0;
  }

  messageInput.addEventListener('input', () => {
    updateSendButtonState();
    messageInput.style.height = 'auto';
    messageInput.style.height = Math.min(messageInput.scrollHeight, 120) + 'px';
  });

  messageInput.addEventListener('keydown', (event) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      sendCurrentInput();
    }
  });

  sendButton.addEventListener('click', sendCurrentInput);

  function sendCurrentInput() {
    const content = messageInput.value.trim();
    if (!content || !currentRoom) {
      return; // E03 빈 메시지는 전송하지 않는다.
    }
    const clientMessageId = crypto.randomUUID();
    appendBubble({ clientMessageId, mine: true, content, status: 'sending' });
    scrollMessagesToBottom();

    messageInput.value = '';
    messageInput.style.height = 'auto';
    updateSendButtonState();

    dispatchSend(currentRoom.roomId, clientMessageId, content);
  }

  function dispatchSend(roomId, clientMessageId, content) {
    const sent = WebTalkSocket.publish(roomId, clientMessageId, content);
    const row = findBubbleRow(clientMessageId);

    if (!sent) {
      if (row) setBubbleStatus(row, 'failed');
      return;
    }

    const timer = setTimeout(() => {
      const staleRow = findBubbleRow(clientMessageId);
      if (staleRow && staleRow.querySelector('.bubble').classList.contains('state-sending')) {
        setBubbleStatus(staleRow, 'failed');
      }
      pendingTimers.delete(clientMessageId);
    }, SEND_TIMEOUT_MS);
    pendingTimers.set(clientMessageId, timer);
  }

  function retrySend(row) {
    const clientMessageId = row.dataset.clientId;
    const content = row.dataset.content;
    if (!currentRoom) return;
    setBubbleStatus(row, 'sending');
    dispatchSend(currentRoom.roomId, clientMessageId, content);
  }

  function handleIncomingMessage(message) {
    if (!currentRoom || message.roomId !== currentRoom.roomId) {
      return;
    }

    const isMine = message.senderId === currentUser.userId;
    if (isMine) {
      const row = findBubbleRow(message.clientMessageId);
      const timer = pendingTimers.get(message.clientMessageId);
      if (timer) {
        clearTimeout(timer);
        pendingTimers.delete(message.clientMessageId);
      }
      if (row) {
        setBubbleStatus(row, 'sent', message.createdAt);
      } else {
        appendBubble(buildBubbleModel(message, 'sent'));
      }
    } else {
      appendBubble(buildBubbleModel(message, 'sent'));
    }
    scrollMessagesToBottom();
    updateRoomListPreview(message.roomId, message.content);
  }

  function updateRoomListPreview(roomId, content) {
    const li = roomListEl.querySelector(`li[data-room-id="${roomId}"]`);
    if (li) {
      const preview = li.querySelector('.last-message');
      if (preview) preview.textContent = content;
      roomListEl.prepend(li);
    }
  }

  // ---------- 연결 상태 표시 (FR08, E07, 4.5 접근성) ----------
  function handleConnectionStatusChange(status, attempt, max) {
    latestConnectionStatus = status;
    applyConnectionStatusUI(status, attempt, max);
  }

  function applyConnectionStatusUI(status, attempt, max) {
    connectionStatusEl.classList.remove(
        'status-connecting', 'status-connected', 'status-reconnecting', 'status-disconnected');

    if (status === 'connected') {
      connectionStatusEl.classList.add('status-connected');
      connectionStatusEl.textContent = '연결됨';
      connectionStatusEl.onclick = null;
      connectionStatusEl.style.cursor = 'default';
    } else if (status === 'reconnecting') {
      connectionStatusEl.classList.add('status-reconnecting');
      connectionStatusEl.textContent = `재연결 중 (${attempt}/${max})`;
      connectionStatusEl.onclick = null;
      connectionStatusEl.style.cursor = 'default';
    } else if (status === 'disconnected') {
      connectionStatusEl.classList.add('status-disconnected');
      connectionStatusEl.textContent = '연결 끊김 (클릭하여 재연결)';
      connectionStatusEl.style.cursor = 'pointer';
      connectionStatusEl.onclick = () => WebTalkSocket.retryNow();
    } else {
      connectionStatusEl.classList.add('status-connecting');
      connectionStatusEl.textContent = '연결 중';
      connectionStatusEl.onclick = null;
      connectionStatusEl.style.cursor = 'default';
    }
  }

  function handleRoomError(error) {
    if (error.code === 'CHAT_ROOM_FORBIDDEN') {
      alert(error.message || '이 대화방에 접근할 수 없습니다.');
      closeRoom();
      loadRoomList();
    } else {
      alert(error.message || '오류가 발생했습니다.');
    }
  }

  init();
})();
