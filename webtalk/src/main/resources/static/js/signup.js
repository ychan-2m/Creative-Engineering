// FR01 회원가입.
(() => {
  const form = document.getElementById('signupForm');
  const errorBanner = document.getElementById('errorBanner');
  const signupButton = document.getElementById('signupButton');

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
    const nickname = document.getElementById('nickname').value.trim();
    const password = document.getElementById('password').value;
    const passwordConfirm = document.getElementById('passwordConfirm').value;

    if (password.length < 8) {
      showError('비밀번호는 8자 이상으로 입력해 주세요.');
      return;
    }
    if (password !== passwordConfirm) {
      showError('비밀번호 확인이 일치하지 않습니다.');
      return;
    }

    signupButton.disabled = true;
    try {
      await WebTalkApi.post('/api/auth/signup', { loginId, nickname, password });
      alert('가입이 완료되었습니다. 로그인해 주세요.');
      location.href = '/login.html';
    } catch (err) {
      showError(err.message || '가입에 실패했습니다.');
    } finally {
      signupButton.disabled = false;
    }
  });
})();
