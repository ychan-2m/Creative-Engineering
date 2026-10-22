// 대화 목록/검색/채팅방/실시간 송수신을 모두 담당하는 메인 화면 스크립트.
(() => {
  const SEND_TIMEOUT_MS = 5000; // 이 시간 안에 서버 확인(같은 clientMessageId의 브로드캐스트)이 없으면 실패 처리한다.

  let currentUser = null;       // { userId, loginId, nickname }
  let currentRoom = null;       // { roomId, peer: { userId, nickname } }
  let lastDisplayedMessageId = 0, historyLoading = false, reading = false, refreshing = false;
  let roomListRevision = 0, roomRevision = 0, syncedMessageId = 0;
  let oldestMessageId = 0, olderLoading = false, hasOlder = false;
  let roomRefreshTimer = null, searchRevision = 0, readAgain = false;
  const acknowledgedReads = new Map();
  const roomDrafts = new Map();
  const outbox = new Map(); // 서버 확인 전 메시지는 방을 바꿔도 재시도할 수 있도록 보관한다.
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
  const olderButton = document.getElementById('olderButton');
  const historyStatus = document.getElementById('historyStatus');
  const latestMessagesButton = document.getElementById('latestMessagesButton');

  // ---------- 초기화 ----------
  async function init() {
    try {
      currentUser = await WebTalkApi.get('/api/me');
    } catch (err) {
      location.href = '/login.html';
      return;
    }
    currentUserLabel.textContent = `${currentUser.nickname} (${currentUser.loginId})`;

    WebTalkReplyAssist.setUser(currentUser.userId);
    await loadRoomList().catch(console.error);
    setInterval(() => {
      if (!document.hidden) {
        loadRoomList().catch(console.error);
        refreshHistory().catch(console.error);
        markDisplayedRead().catch(console.error);
      }
    }, 15000);

    WebTalkSocket.connect({
      onStatusChange: handleConnectionStatusChange,
      onRoomError: handleRoomError,
      onRoomReady: () => refreshHistory().catch(console.error),
      onRoomsChanged: () => {
        clearTimeout(roomRefreshTimer);
        roomRefreshTimer = setTimeout(() => {
          loadRoomList().catch(console.error);
          refreshHistory().catch(console.error);
        }, 100);
      },
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
    const revision = ++searchRevision;
    const query = searchInput.value.trim();
    searchResults.innerHTML = '';
    searchEmpty.hidden = true;
    if (!query) {
      return;
    }
    let results;
    try {
      results = await WebTalkApi.get(`/api/users?query=${encodeURIComponent(query)}`);
    } catch (err) {
      if (revision !== searchRevision) return;
      searchEmpty.textContent = err.message || '검색하지 못했습니다. 다시 시도해 주세요.';
      searchEmpty.hidden = false;
      return;
    }
    if (revision !== searchRevision) return;
    searchEmpty.textContent = '해당 사용자를 찾을 수 없습니다.';
    if (results.length === 0) {
      searchEmpty.hidden = false;
      return;
    }
    results.forEach((user) => {
      const li = document.createElement('li');
      li.textContent = `${user.nickname} (${user.loginId})`;
      makeActionable(li, () => openDirectRoomWith(user));
      searchResults.appendChild(li);
    });
  }

  searchButton.addEventListener('click', runSearch);
  searchInput.addEventListener('keydown', (event) => {
    if (event.key === 'Enter' && !event.isComposing) {
      event.preventDefault();
      runSearch();
    }
  });

  async function openDirectRoomWith(user) {
    try {
      const room = await WebTalkApi.post('/api/chat-rooms/direct', { targetUserId: user.userId });
      searchRevision++;
      searchResults.innerHTML = '';
      searchInput.value = '';
      searchEmpty.hidden = true;
      await loadRoomList();
      await openRoom(room.roomId, room.peer);
    } catch (err) {
      searchEmpty.textContent = err.message || '대화방을 열지 못했습니다.';
      searchEmpty.hidden = false;
    }
  }

  function makeActionable(element, action) {
    element.tabIndex = 0;
    element.setAttribute('role', 'button');
    element.addEventListener('click', action);
    element.addEventListener('keydown', event => {
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        action();
      }
    });
  }

  // ---------- 대화 목록 (FR05 목록 조회) ----------
  async function loadRoomList() {
    const requested = ++roomListRevision;
    const rooms = await WebTalkApi.get('/api/chat-rooms');
    if (requested !== roomListRevision) return;
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

      makeActionable(li, () => openRoom(room.roomId, room.peer));
      roomListEl.appendChild(li);
    });
  }

  function markActiveRoomInList(roomId) {
    Array.from(roomListEl.children).forEach((li) => {
      li.classList.toggle('active', Number(li.dataset.roomId) === roomId);
    });
  }

  // ---------- 채팅방 열기 (FR07 대화 기록 조회) ----------
  function saveRoomDraft() {
    if (currentRoom) roomDrafts.set(currentRoom.roomId, messageInput.value);
  }

  function restoreRoomDraft() {
    messageInput.value = currentRoom ? roomDrafts.get(currentRoom.roomId) || '' : '';
    messageInput.dispatchEvent(new Event('input'));
  }

  async function openRoom(roomId, peer) {
    if (currentRoom?.roomId === roomId && !historyStatus.dataset.failed) return;
    saveRoomDraft();
    const revision = ++roomRevision;
    currentRoom = { roomId, peer };
    restoreRoomDraft();
    lastDisplayedMessageId = 0;
    syncedMessageId = 0;
    oldestMessageId = 0;
    refreshing = false;
    olderLoading = false;
    hasOlder = false;
    olderButton.hidden = true;
    olderButton.disabled = false;
    olderButton.textContent = '이전 메시지 불러오기';
    latestMessagesButton.hidden = true;
    delete historyStatus.dataset.failed;
    historyStatus.textContent = '대화 기록을 불러오는 중…';
    historyLoading = true;
    WebTalkReplyAssist.setRoom(roomId);
    peerNameLabel.textContent = peer.nickname;
    messageListEl.innerHTML = '';
    for (const [clientMessageId, pending] of outbox) {
      if (pending.roomId === roomId) appendBubble({ ...pending, clientMessageId, mine: true });
    }

    chatPlaceholder.hidden = true;
    chatRoomView.hidden = false;
    chatBody.classList.add('room-open');
    markActiveRoomInList(roomId);
    applyConnectionStatusUI(latestConnectionStatus);

    WebTalkSocket.subscribeRoom(roomId, handleIncomingMessage);

    try {
      const history = await WebTalkApi.get(`/api/chat-rooms/${roomId}/messages`);
      if (revision !== roomRevision) return;
      history.forEach(renderConfirmedMessage);
      syncedMessageId = history.at(-1)?.messageId || 0;
      oldestMessageId = history[0]?.messageId || 0;
      hasOlder = history.length === 50;
      olderButton.hidden = !hasOlder;
      historyStatus.textContent = history.length ? '' : '아직 메시지가 없습니다.';
      historyLoading = false;
      scrollMessagesToBottom();
      refreshHistory().catch(err => {
        if (revision === roomRevision) historyStatus.textContent = '최근 대화를 동기화하지 못했습니다. 연결이 복구되면 다시 시도합니다.';
      });
    } catch (err) {
      if (revision !== roomRevision) return;
      historyLoading = false;
      historyStatus.dataset.failed = 'true';
      historyStatus.textContent = '대화 기록을 불러오지 못했습니다. 다시 시도해 주세요.';
      olderButton.hidden = false;
      olderButton.textContent = '다시 불러오기';
      if (err.status === 403) {
        // E06: 권한 없는 방이면 목록으로 돌려보낸다.
        alert('이 대화방에 접근할 수 없습니다.');
        closeRoom();
        await loadRoomList();
      }
    }
  }

  function closeRoom() {
    saveRoomDraft();
    roomRevision++;
    currentRoom = null;
    restoreRoomDraft();
    WebTalkSocket.unsubscribeRoom();
    WebTalkReplyAssist.setRoom(null);
    chatBody.classList.remove('room-open');
    chatPlaceholder.hidden = false;
    chatRoomView.hidden = true;
  }

  backButton.addEventListener('click', () => {
    closeRoom();
    loadRoomList().catch(console.error);
  });

  function renderConfirmedMessage(message) {
    lastDisplayedMessageId = Math.max(lastDisplayedMessageId, message.messageId);
    const confirmed = messageListEl.querySelector(`[data-message-id="${message.messageId}"]`);
    const row = confirmed || (message.senderId === currentUser.userId ? findBubbleRow(message.clientMessageId) : null);
    if (row) {
      row.dataset.messageId = message.messageId;
      setBubbleStatus(row, 'sent', message.createdAt);
    } else {
      appendBubble(buildBubbleModel(message, 'sent'));
    }
    const timer = pendingTimers.get(message.clientMessageId);
    if (message.senderId === currentUser.userId) outbox.delete(message.clientMessageId);
    if (message.senderId === currentUser.userId && timer) {
      clearTimeout(timer);
      pendingTimers.delete(message.clientMessageId);
    }
    // 실시간 수신과 기록 응답이 섞여도 서버 ID 순으로 배치한다.
    const rows = Array.from(messageListEl.children);
    rows.sort((a, b) => (Number(a.dataset.messageId) || Infinity) - (Number(b.dataset.messageId) || Infinity));
    rows.forEach(row => messageListEl.appendChild(row));
  }

  olderButton.addEventListener('click', async () => {
    if (!currentRoom || olderLoading) return;
    if (!oldestMessageId || historyStatus.dataset.failed) {
      openRoom(currentRoom.roomId, currentRoom.peer);
      return;
    }
    const revision = roomRevision, roomId = currentRoom.roomId;
    olderLoading = true;
    olderButton.disabled = true;
    historyStatus.textContent = '';
    const oldHeight = messageListEl.scrollHeight, oldTop = messageListEl.scrollTop;
    try {
      const history = await WebTalkApi.get(`/api/chat-rooms/${roomId}/messages?before=${oldestMessageId}`);
      if (revision !== roomRevision) return;
      history.forEach(renderConfirmedMessage);
      if (history.length) oldestMessageId = history[0].messageId;
      hasOlder = history.length === 50;
      olderButton.hidden = !hasOlder;
      messageListEl.scrollTop = oldTop + messageListEl.scrollHeight - oldHeight;
    } catch (err) {
      if (revision === roomRevision) historyStatus.textContent = '이전 대화를 불러오지 못했습니다. 다시 시도해 주세요.';
    } finally {
      if (revision === roomRevision) {
        olderLoading = false;
        olderButton.disabled = false;
      }
    }
  });

  function isAtLatestMessage() {
    return messageListEl.scrollHeight - messageListEl.scrollTop - messageListEl.clientHeight < 24;
  }

  latestMessagesButton.addEventListener('click', () => {
    scrollMessagesToBottom();
    markDisplayedRead().catch(console.error);
  });
  messageListEl.addEventListener('scroll', () => {
    if (isAtLatestMessage()) latestMessagesButton.hidden = true;
    markDisplayedRead().catch(console.error);
  });

  async function markDisplayedRead() {
    if (!currentRoom || document.hidden || historyLoading || refreshing || !isAtLatestMessage() || !lastDisplayedMessageId) return;
    const roomId = currentRoom.roomId, messageId = lastDisplayedMessageId;
    if ((acknowledgedReads.get(roomId) || 0) >= messageId) return;
    if (reading) { readAgain = true; return; }
    reading = true;
    try {
      await WebTalkApi.post(`/api/chat-rooms/${roomId}/read`, { messageId });
      acknowledgedReads.set(roomId, messageId);
      await loadRoomList();
    } finally {
      reading = false;
      if (readAgain) {
        readAgain = false;
        markDisplayedRead().catch(console.error);
      }
    }
  }

  async function refreshHistory() {
    if (!currentRoom || historyLoading || historyStatus.dataset.failed || refreshing || document.hidden) return;
    const roomId = currentRoom.roomId, revision = roomRevision;
    const wasAtBottom = messageListEl.scrollHeight - messageListEl.scrollTop - messageListEl.clientHeight < 80;
    refreshing = true;
    try {
      let page;
      do {
        page = await WebTalkApi.get(`/api/chat-rooms/${roomId}/messages?after=${syncedMessageId}`);
        if (revision !== roomRevision) return;
        page.forEach(renderConfirmedMessage);
        if (page.length) syncedMessageId = page.at(-1).messageId;
      } while (page.length === 50);
      historyStatus.textContent = '';
      if (wasAtBottom) scrollMessagesToBottom();
    } finally {
      if (revision === roomRevision) refreshing = false;
    }
    if (revision === roomRevision) await markDisplayedRead();
  }
  document.addEventListener('visibilitychange', () => {
    if (!document.hidden) refreshHistory().catch(console.error);
  });

  // ---------- 메시지 렌더링 ----------
  function buildBubbleModel(message, status) {
    return {
      messageId: message.messageId,
      roomId: message.roomId,
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
    if (model.messageId) row.dataset.messageId = model.messageId;
    if (model.clientMessageId) {
      row.dataset.clientId = model.clientMessageId;
    }
    row.dataset.content = model.content;
    if (!model.mine && model.messageId) {
      row.dataset.messageId = model.messageId;
      row.classList.add('replyable');
      row.tabIndex = 0;
      row.setAttribute('role', 'button');
      row.setAttribute('aria-label', `이 메시지에 답장 추천: ${model.content}`);
      row.setAttribute('aria-pressed', 'false');
      row.title = '눌러서 이 메시지에 답장 추천';
      const select = () => WebTalkReplyAssist.selectMessage(model);
      row.addEventListener('click', select);
      row.addEventListener('keydown', event => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault();
          select();
        }
      });
    }

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
    if (model.status === 'failed') setBubbleStatus(row, 'failed');
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
    if (!clientMessageId) return null;
    return messageListEl.querySelector(`.bubble-row.mine[data-client-id="${CSS.escape(clientMessageId)}"]`);
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
    latestMessagesButton.hidden = true;
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
    if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
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
    // LAN의 일반 HTTP 주소에서도 안전한 난수 기반 UUID를 만들 수 있게 한다.
    const clientMessageId = crypto.randomUUID ? crypto.randomUUID() :
      '10000000-1000-4000-8000-100000000000'.replace(/[018]/g, c =>
        (Number(c) ^ crypto.getRandomValues(new Uint8Array(1))[0] & 15 >> Number(c) / 4).toString(16));
    outbox.set(clientMessageId, { roomId: currentRoom.roomId, content, status: 'sending' });
    appendBubble({ clientMessageId, mine: true, content, status: 'sending' });
    scrollMessagesToBottom();

    roomDrafts.delete(currentRoom.roomId);
    messageInput.value = '';
    messageInput.dispatchEvent(new Event('input'));
    messageInput.style.height = 'auto';
    updateSendButtonState();

    WebTalkReplyAssist.clearTarget();
    dispatchSend(currentRoom.roomId, clientMessageId, content);
  }

  function dispatchSend(roomId, clientMessageId, content) {
    clearTimeout(pendingTimers.get(clientMessageId));
    pendingTimers.delete(clientMessageId);
    const pending = outbox.get(clientMessageId);
    if (pending) pending.status = 'sending';
    const sent = WebTalkSocket.publish(roomId, clientMessageId, content);
    const row = findBubbleRow(clientMessageId);

    if (!sent) {
      if (pending) pending.status = 'failed';
      if (row) setBubbleStatus(row, 'failed');
      return;
    }

    const timer = setTimeout(() => {
      if (outbox.has(clientMessageId)) outbox.get(clientMessageId).status = 'failed';
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

    const wasAtBottom = messageListEl.scrollHeight - messageListEl.scrollTop - messageListEl.clientHeight < 80;
    WebTalkReplyAssist.onIncomingMessage();
    renderConfirmedMessage(message);
    historyStatus.textContent = '';
    if (wasAtBottom || message.senderId === currentUser.userId) scrollMessagesToBottom();
    else latestMessagesButton.hidden = false;
    updateRoomListPreview(message.roomId, message.content);
    markDisplayedRead().catch(console.error);
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
    if (status === 'connected') loadRoomList().catch(console.error);
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
      loadRoomList().catch(console.error);
    } else {
      alert(error.message || '오류가 발생했습니다.');
    }
  }

  init();
})();
