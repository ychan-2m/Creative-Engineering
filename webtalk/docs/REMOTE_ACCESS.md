# 다른 네트워크에서 WebTalk 사용하기

모든 사용자가 **같은 서버 주소**로 접속해야 합니다. 각자 localhost를 열면 각자의 컴퓨터에 접속하므로 서로 대화할 수 없습니다.
메시지와 질문지는 서버의 `data/`에 저장됩니다. 상대방 컴퓨터에는 Java나 Ollama가 필요하지 않습니다.

## 이 컴퓨터를 서버로 사용하는 임시 접속

1. macOS에서 `brew install cloudflared`로 접속 도구를 설치합니다.
2. webtalk 폴더에서 `./mvnw spring-boot:run`으로 서버를 실행합니다.
3. 같은 폴더에서 새 터미널을 열고 `./scripts/share.sh`를 실행합니다.
4. 출력된 `https://…trycloudflare.com` 주소를 두 사용자가 모두 엽니다. Wi-Fi와 휴대폰 데이터처럼 서로 다른 네트워크여도 같은 주소를 사용합니다.
5. 각자 회원가입·로그인한 뒤 상대 아이디를 검색하여 대화합니다.

공유용 URL은 실행할 때 생성됩니다. 서버와 터널을 모두 켜 두어야 하며, 컴퓨터가 잠자기에 들어가거나 터널을 종료하면 접속이 끊깁니다. 영구 주소가 필요한 경우 아래 방법을 사용하세요.
Cloudflare Quick Tunnel은 임시 주소를 제공하며 테스트 용도입니다. [공식 안내](https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/).

## 상시 운영 서버

인터넷에서 접근 가능한 서버에 Java 17을 설치하고 프로젝트를 복사한 뒤 다음 명령을 실행합니다.

```sh
./mvnw clean package
java -jar target/webtalk-0.0.1-SNAPSHOT.jar
```

서버는 기본으로 `0.0.0.0:8080`에서 요청을 받습니다. `PORT`와 `SERVER_ADDRESS` 환경 변수로 변경할 수 있습니다.
도메인을 서버에 연결하고 HTTPS 프록시를 앞에 두세요. 아래 Nginx 예시를 기존 HTTPS server 블록에 추가할 수 있습니다.

```nginx
location / {
    proxy_pass http://127.0.0.1:8080;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Host $host;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_read_timeout 3600s;
}
```

HTTPS 전용 운영에서는 `WEBTALK_SECURE_COOKIE=true`로 실행합니다. 프록시를 사용하는 경우 앱의 8080 포트는 외부에서 직접 접근하지 않도록 서버 방화벽을 설정합니다. 전달 헤더는 신뢰하는 프록시에서 처리해야 합니다.
브라우저는 접속한 주소로 API와 WebSocket을 요청하며 HTTPS에서는 자동으로 WSS를 사용합니다. WebSocket은 동일 출처만 허용합니다.
H2 관리 콘솔은 기본으로 비활성화됩니다. 로컬 개발에서만 필요하면 `WEBTALK_H2_CONSOLE=true`를 사용하세요.
현재 파일 DB와 실시간 브로커는 서버 한 대 기준입니다. `data/`를 보존하고 백업하세요.
AI 답장 추천은 서버의 Ollama를 사용합니다. 서버에서 AI를 실행하지 않아도 일반 채팅은 가능합니다.

## 변경된 화면 동작

- 대화 목록 숫자는 상대방이 보낸 메시지 중 아직 읽지 않은 개수입니다. 내가 보낸 메시지는 제외합니다.
- 대화방을 열어 메시지를 표시하면 읽음 처리합니다. 다른 탭으로 이동하거나 모바일에서 목록으로 돌아가면 이후 메시지는 읽지 않은 것으로 남습니다.
- 다른 방에 온 메시지의 숫자와 미리보기는 화면이 열린 동안 약 3초마다 갱신됩니다.
- 내 말투 설정에서 친구·어른·선배/동료·후배·처음 만난 사람의 질문지를 각각 저장합니다. 질문지 전환 중 작성 내용은 창 안에 임시 보관하며, 서버에 반영하려면 관계마다 저장을 누릅니다.
- 대화 화면의 상대방과의 관계를 고르면 해당 관계의 질문지만 답장 추천에 사용합니다. 선택은 현재 브라우저에 사용자·방별로 보관됩니다.
- 이전 단일 질문지 답변은 해당 관계의 질문지로 유지합니다. 선택한 관계의 질문지가 없으면 먼저 작성하라는 안내를 표시합니다.
