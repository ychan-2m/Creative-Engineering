// FR02 로그인. 실패 시(E01) 비밀번호 입력값을 비우고 오류 문구를 보여준다.
(() => {
  const form = document.getElementById('loginForm');
  const errorBanner = document.getElementById('errorBanner');
  const loginButton = document.getElementById('loginButton');
  const passwordInput = document.getElementById('password');

  function showError(message) {
    errorBanner.textContent = message;
    errorBanner.hidden = false;
  }

  function hideError() {
    errorBanner.hidden = true;
  }

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    hideError();

    const loginId = document.getElementById('loginId').value.trim();
    const password = passwordInput.value;

    if (!loginId || !password) {
      showError('아이디와 비밀번호를 모두 입력해 주세요.');
      return;
    }

    loginButton.disabled = true;
    try {
      await WebTalkApi.post('/api/auth/login', { loginId, password });
      location.href = '/chat.html';
    } catch (err) {
      showError(err.message || '아이디 또는 비밀번호가 올바르지 않습니다.');
      passwordInput.value = '';
      passwordInput.focus();
    } finally {
      loginButton.disabled = false;
    }
  });
})();
