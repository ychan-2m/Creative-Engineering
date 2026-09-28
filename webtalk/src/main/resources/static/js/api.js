// 모든 REST 호출을 감싸는 공통 헬퍼. 세션 쿠키를 같이 보내고(same-origin),
// 오류 응답은 부록 B 형식({code, message})을 그대로 살려서 던진다.
window.WebTalkApi = (() => {

  class ApiError extends Error {
    constructor(status, code, message) {
      super(message);
      this.status = status;
      this.code = code;
    }
  }

  async function request(path, options = {}) {
    const response = await fetch(path, {
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
      ...options,
    });

    if (response.status === 204) {
      return null;
    }

    const isJson = (response.headers.get('content-type') || '').includes('application/json');
    const body = isJson ? await response.json().catch(() => null) : null;

    if (!response.ok) {
      const code = body && body.code ? body.code : 'UNKNOWN_ERROR';
      const message = body && body.message ? body.message : '요청을 처리하지 못했습니다.';
      throw new ApiError(response.status, code, message);
    }

    return body;
  }

  return {
    ApiError,
    get: (path) => request(path, { method: 'GET' }),
    post: (path, data) => request(path, { method: 'POST', body: JSON.stringify(data ?? {}) }),
  };
})();
