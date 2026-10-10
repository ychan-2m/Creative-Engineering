#!/bin/sh
set -eu
if ! command -v cloudflared >/dev/null 2>&1; then
  echo 'cloudflared를 먼저 설치하세요: macOS에서는 brew install cloudflared' >&2
  echo '설치 안내: https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/downloads/' >&2
  exit 1
fi
WEBTALK_LOCAL_PORT=${PORT:-8080}
if ! curl -fsS "http://127.0.0.1:${WEBTALK_LOCAL_PORT}/login.html" >/dev/null; then
  echo '먼저 webtalk 폴더에서 ./mvnw spring-boot:run 으로 서버를 실행하세요.' >&2
  exit 1
fi
echo '출력되는 https://…trycloudflare.com 주소를 두 사용자에게 공유하세요.'
echo '서버와 이 터미널을 켜 두세요. 종료 후 다시 실행하면 주소가 바뀝니다.'
exec cloudflared tunnel --url "http://127.0.0.1:${WEBTALK_LOCAL_PORT}"
