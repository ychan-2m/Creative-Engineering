window.WebTalkReplyAssist = (() => {
  const input = document.getElementById('messageInput');
  const button = document.getElementById('suggestButton');
  const status = document.getElementById('suggestStatus');
  const list = document.getElementById('suggestions');
  const dialog = document.getElementById('styleDialog');
  const form = document.getElementById('styleForm');
  const fields = document.getElementById('styleQuestions');
  const rewrites = document.getElementById('styleRewrites');
  const styleStatus = document.getElementById('styleStatus');
  const relationship = document.getElementById('styleRelationship');
  const replyRelationship = document.getElementById('replyRelationship');
  let userId = null, loadedRelationship = null, surveyRevision = 0;
  const drafts = new Map();
  const avoided = document.getElementById('styleAvoided');
  const targetBox = document.getElementById('replyTarget');
  const targetText = document.getElementById('replyTargetText');
  const preferenceDefinitions = [
    ['formality', '말의 높임', [['examples', '답변 예시대로'], ['casual', '반말'], ['polite', '존댓말']]],
    ['length', '답장 길이', [['short', '짧게'], ['balanced', '보통'], ['detailed', '자세히']]],
    ['spacing', '띄어쓰기', [['standard', '일반적인 띄어쓰기'], ['relaxed', '가끔 생략'], ['compact', '최소한으로']]],
    ['acknowledgment', '긍정할 때 쓰는 표현', [['examples', '답변 예시대로'], ['ㅇㅇ', 'ㅇㅇ'], ['응', '응'], ['웅', '웅'], ['네', '네']]],
    ['laughter', '웃음 표현', [['examples', '답변 예시대로'], ['none', '사용 안 함'], ['ㅋㅋ', 'ㅋㅋ'], ['ㅎㅎ', 'ㅎㅎ']]],
    ['emoji', '이모지', [['none', '사용 안 함'], ['sometimes', '가끔'], ['often', '자주']]],
  ];
  let roomId = null, selected = null;
  let revision = 0, busy = false, editing = false, queuedSelection = false;

  for (const [key, title, options] of preferenceDefinitions) {
    const label = document.createElement('label');
    label.htmlFor = `style-${key}`; label.textContent = title;
    const select = document.createElement('select'); select.id = label.htmlFor;
    for (const [value, text] of options) select.add(new Option(text, value));
    document.getElementById('stylePreferences').append(label, select);
  }
  function invalidate() {
    revision++;
    button.textContent = selected ? '선택한 메시지 답장 추천' : input.value.trim() ? '내 말투로 다듬기' : '내 말투로 답장 추천';
    list.replaceChildren();
    status.textContent = busy ? '생성 중입니다. 선택이나 입력이 바뀌면 이전 결과를 표시하지 않습니다.' : '';
  }
  input.addEventListener('input', () => { queuedSelection = false; invalidate(); });
  function displayTarget() {
    targetBox.hidden = !selected;
    targetText.textContent = selected?.content || '';
    document.querySelectorAll('.replyable').forEach(row => {
      const active = !!selected && Number(row.dataset.messageId) === selected.messageId;
      row.classList.toggle('reply-selected', active);
      row.setAttribute('aria-pressed', String(active));
    });
  }
  function clearTarget() { selected = null; queuedSelection = false; displayTarget(); invalidate(); }
  function setUser(value) { userId = value; }
  function setRoom(value) {
    roomId = value;
    try { replyRelationship.value = localStorage.getItem(`webtalk-relationship-${userId}-${roomId}`) || '친한 친구'; } catch (_) {}
    clearTarget();
  }
  replyRelationship.addEventListener('change', () => {
    try { localStorage.setItem(`webtalk-relationship-${userId}-${roomId}`, replyRelationship.value); } catch (_) {}
    invalidate();
  });
  document.getElementById('clearReplyTarget').addEventListener('click', clearTarget);
  function selectMessage(message) {
    if (message.mine || !message.messageId || message.roomId !== roomId) return;
    selected = { messageId: message.messageId, content: message.content };
    displayTarget(); invalidate();
    if (busy) { queuedSelection = true; return; }
    requestSuggestions(true);
  }

  function textFields(container, prompts, values, prefix) {
    container.replaceChildren();
    prompts.forEach((prompt, i) => {
      const label = document.createElement('label'); label.htmlFor = `${prefix}${i}`; label.textContent = prompt;
      const answer = document.createElement('textarea'); answer.id = label.htmlFor;
      answer.required = true; answer.maxLength = 300; answer.rows = 2; answer.value = values?.[i] || '';
      container.append(label, answer);
    });
  }
  function captureSurvey() {
    return {
      relationship: loadedRelationship,
      answers: Array.from(fields.querySelectorAll('textarea'), el => el.value),
      rewrites: Array.from(rewrites.querySelectorAll('textarea'), el => el.value),
      preferences: Object.fromEntries(preferenceDefinitions.map(([key]) => [key, document.getElementById(`style-${key}`).value])),
      avoidedExpressions: avoided.value,
    };
  }
  async function loadSurvey() {
    const requested = ++surveyRevision;
    const category = relationship.value;
    loadedRelationship = null;
    styleStatus.textContent = '불러오는 중…';
    fields.replaceChildren(); rewrites.replaceChildren();
    form.querySelector('[type=submit]').disabled = true;
    try {
      const data = await WebTalkApi.get(`/api/me/reply-style?relationship=${encodeURIComponent(category)}`);
      if (requested !== surveyRevision) return;
      const profile = drafts.get(category) || data.profile;
      avoided.value = profile?.avoidedExpressions || '';
      for (const [key, , options] of preferenceDefinitions) {
        document.getElementById(`style-${key}`).value = profile?.preferences?.[key] || options[0][0];
      }
      textFields(fields, data.questions, profile?.answers, 'styleAnswer');
      textFields(rewrites, data.rewritePrompts, profile?.rewrites, 'styleRewrite');
      loadedRelationship = category;
      styleStatus.textContent = profile ? `${category} 질문지를 불러왔습니다.` : `${category}에게 쓰는 말투를 작성해 주세요.`;
      form.querySelector('[type=submit]').disabled = false;
    } catch (err) { if (requested === surveyRevision) styleStatus.textContent = err.message; }
  }
  relationship.addEventListener('change', () => {
    if (loadedRelationship) drafts.set(loadedRelationship, captureSurvey());
    loadSurvey();
  });
  document.getElementById('styleButton').addEventListener('click', () => {
    dialog.showModal();
    relationship.value = replyRelationship.value;
    loadSurvey();
  });
  document.getElementById('closeStyleButton').addEventListener('click', () => dialog.close());
  form.addEventListener('submit', async event => {
    event.preventDefault(); if (editing || !loadedRelationship) return;
    const answers = Array.from(fields.querySelectorAll('textarea'), el => el.value.trim());
    const rewritten = Array.from(rewrites.querySelectorAll('textarea'), el => el.value.trim());
    if (answers.length !== 6 || rewritten.length !== 3 || [...answers, ...rewritten].some(s => !s)) {
      styleStatus.textContent = '상황 질문 6개와 고쳐 쓰기 3개를 모두 작성해 주세요.'; return;
    }
    const preferences = Object.fromEntries(preferenceDefinitions.map(([key]) => [key, document.getElementById(`style-${key}`).value]));
    editing = true; relationship.disabled = true;
    try {
      await WebTalkApi.post('/api/me/reply-style', {
        relationship: loadedRelationship, answers, preferences, rewrites: rewritten, avoidedExpressions: avoided.value.trim(),
      });
      drafts.delete(loadedRelationship);
      invalidate(); styleStatus.textContent = '저장했습니다. 상대방 메시지를 눌러 답장을 추천받으세요.';
    } catch (err) { styleStatus.textContent = err.message; }
    finally { editing = false; relationship.disabled = false; }
  });
  document.getElementById('deleteStyleButton').addEventListener('click', async () => {
    if (editing || !loadedRelationship) return; editing = true; relationship.disabled = true;
    try {
      await WebTalkApi.remove(`/api/me/reply-style?relationship=${encodeURIComponent(loadedRelationship)}`);
      drafts.delete(loadedRelationship);
      form.querySelectorAll('textarea').forEach(el => { el.value = ''; });
      invalidate(); styleStatus.textContent = '저장한 말투를 삭제했습니다.';
    } catch (err) { styleStatus.textContent = err.message; }
    finally { editing = false; relationship.disabled = false; }
  });

  async function requestSuggestions(fromSelection = false) {
    if (!roomId || busy) return;
    invalidate();
    const requestedRevision = revision;
    busy = true; button.disabled = true;
    status.textContent = selected ? '선택한 메시지의 답장을 만드는 중…' : '답장 후보를 만드는 중…';
    try {
      const result = await WebTalkApi.post(`/api/chat-rooms/${roomId}/reply-suggestions`, {
        relationship: replyRelationship.value,
        draft: fromSelection ? '' : input.value, replyToMessageId: selected?.messageId || null,
      });
      if (requestedRevision !== revision) return;
      result.suggestions.forEach(text => {
        const chip = document.createElement('button'); chip.type = 'button'; chip.textContent = text;
        chip.addEventListener('click', () => {
          if (requestedRevision !== revision) return;
          input.value = text; input.dispatchEvent(new Event('input')); input.focus();
        });
        list.append(chip);
      });
      status.textContent = '원하는 후보를 선택하세요. 전송은 직접 눌러 주세요.';
    } catch (err) {
      if (requestedRevision === revision) status.textContent = err.message;
    } finally {
      busy = false; button.disabled = false;
      if (queuedSelection && selected) {
        queuedSelection = false; requestSuggestions(true);
      } else if (requestedRevision !== revision) {
        status.textContent = '선택이나 입력이 바뀌었습니다. 다시 추천해 주세요.';
      }
    }
  }
  button.addEventListener('click', () => requestSuggestions());
  function onIncomingMessage() { if (!selected) invalidate(); }
  return { setUser, setRoom, invalidate, selectMessage, clearTarget, onIncomingMessage };
})();
