# WebTalk

(창의공학설계 개인 프로젝트) 설계를 그대로 구현한
1대1 웹 메신저 프로토타입입니다. 설치 없이 브라우저 주소로 접속해 회원가입 → 로그인 →
사용자 검색 → 1대1 채팅 → 실시간 송수신 → 대화 기록 보존까지 동작합니다.

## 기술 스택 (보고서 5.1)

| 영역 | 선택 |
| --- | --- |
| 프론트엔드 | 순수 HTML / CSS / JavaScript (프레임워크 없음) |
| 백엔드 | Java 17, Spring Boot 4 (Web MVC, Security, Data JPA, WebSocket, Validation) |
| 실시간 통신 | WebSocket + STOMP (`/ws`, `/topic`, `/queue`, `/app`) |
| 데이터 저장 | H2 (파일 모드) — 부록 A 스키마 그대로 사용, MySQL 전환 시 schema.sql만 조정 |
| 인증 | Spring Security 세션 (BCrypt 해시) |

## 실행 방법

```bash
cd webtalk
./mvnw spring-boot:run
```

브라우저에서 `http://localhost:8080` 접속 → 회원가입 → 로그인 → 채팅.
두 명이 대화하는 흐름을 확인하려면 일반 창(user01)과 시크릿 창(user02)을 각각 로그인하세요
(6.6 구현 점검 체크리스트의 "시연" 항목).

H2 콘솔은 `WEBTALK_H2_CONSOLE=true`로 실행한 경우 `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:file:./data/webtalk`, 사용자 `sa`,
비밀번호 없음)에서 개발용으로만 확인할 수 있습니다.

## 폴더 구조 (6.1 대응)

```
src/main/java/com/example/webtalk/
├─ auth/        AuthController, SecurityConfig, CustomUserDetailsService, WebtalkUserPrincipal
├─ user/        User, UserRepository, UserService, UserController
├─ chat/        ChatRoom, ChatRoomMember(Id), ChatRoomService, ChatRoomController
├─ message/     Message, MessageService, ChatMessageController
├─ websocket/   WebSocketConfig, WebSocketAuthInterceptor, ChatWebSocketController
└─ common/      ErrorResponse, 예외 클래스, GlobalExceptionHandler

src/main/resources/
├─ schema.sql           (부록 A 데이터베이스 생성문)
├─ static/*.html         login.html, signup.html, chat.html
├─ static/css/app.css
└─ static/js/            api.js, login.js, signup.js, socket.js, chat.js
```

## 보고서 대비 구현 범위

- **구현됨**: FR01~FR09 (회원가입, 로그인/로그아웃, 사용자 검색, 1대1 방 생성/조회,
  실시간 송수신, 대화 기록 조회, 연결 상태 표시, 전송 실패 재시도), NFR01~NFR06,
  E01~E10 예외 흐름, 5.7 서버 권한 검사 순서, 5.8 보안 기준(BCrypt, 방 구성원 검사,
  textContent 렌더링, 세션 무효화).
- **구현됨**: FR10(안 읽은 수) — 상대방이 보낸 안 읽은 메시지만 집계하고 `POST /api/chat-rooms/{roomId}/read`로 읽음 처리합니다.
- **범위 제외** (1.5, 9.1과 동일): 단체 채팅, 파일/음성/영상, 카카오 계정 연동, 종단간 암호화,
  다중 서버 확장.

### 보고서 예시에서 의도적으로 바꾼 부분

5.6의 STOMP MESSAGE 예시에는 `clientMessageId`가 없지만, 발신자 화면이 자신의 SENDING
말풍선을 서버 확인 메시지와 맞춰 SENT로 바꾸려면(4.4) 상관관계 값이 필요합니다. 그래서
`MessageResponse`에 `clientMessageId`를 추가했습니다. 그 외 코드는 보고서 6.4/6.5 뼈대와
5.5~5.8 명세를 그대로 따릅니다.

## 확인한 동작 (수동 스모크 테스트)

curl과 WebSocket(STOMP) 클라이언트로 아래를 직접 확인했습니다.

- 회원가입/중복 아이디(409)/로그인 실패(401, E01)/로그아웃 후 401(AC07 관련)
- 사용자 검색 시 본인 제외 (FR04)
- 1대1 방 생성 후 재요청 시 같은 방 반환 (E05), 자기 자신과의 방 생성 차단
- 방 구성원이 아닌 사용자의 조회(403)와 구독 차단 → `/user/queue/errors` (E06, 5.8)
- 실시간 송수신: 두 세션이 같은 메시지를 즉시 수신 (AC03)
- 같은 `clientMessageId` 재전송 시 새로 저장하지 않고 동일 `messageId` 반환 (NFR03, T06, E09)
- 빈 메시지 전송 거부 (E03)

자동 테스트와 두 브라우저 세션의 검증 방법은 [개선 및 검증 기록](docs/UPGRADE_2026-10-22.md)에 정리했습니다.
서로 다른 실제 네트워크에서의 시연과 보고서 7장 수동 점검표는 별도로 확인해야 합니다.

## 남은 할 일 (9.2 개선 우선순위 그대로)

1. 오프라인 알림(Service Worker/Web Push)
2. 파일 전송
3. 다중 서버 확장(Redis 등 외부 브로커)
4. 서로 다른 실제 네트워크에서 시연 및 실제 모바일 기기 점검


## 내 말투 답장 추천

채팅 화면의 **내 말투 설정**에서 말투 선택 6개·상황별 답변 6개·문장 고쳐 쓰기 3개를 저장합니다.
**상대방 메시지를 클릭**하면 그 메시지에 맞는 답장 초안을 추천합니다. 후보를 선택한 뒤 직접 전송합니다.
기존 질문지 답변은 유지됩니다. 로컬 Ollama 실행과 자세한 사용 방법은 [AI 답장 추천 안내](docs/AI_REPLY.md)를 참고하세요.

## 관계별 질문지와 외부 접속

친구·어른·선배/동료·후배·처음 만난 사람의 질문지를 각각 저장하고 대화 화면에서 사용할 관계를 선택합니다.
다른 네트워크에서 같은 서버로 접속하는 방법은 [외부 접속 안내](docs/REMOTE_ACCESS.md)를 참고하세요.

## 2026-10-22 개선

- **이전 대화**: 최근 50개 아래에 숨겨졌던 기록을 “이전 메시지 불러오기”로 조회합니다.
- **재연결 복구**: 마지막으로 조회한 ID 이후 메시지를 50개씩 반복 조회하므로, 연결이 끊긴 동안 50개 넘게 쌓여도 복구합니다.
- **실시간 목록**: 새 방·메시지·읽음 처리를 개인 큐로 알립니다. 15초 주기 조회는 알림 누락 시 보완합니다.
- **동시 요청과 권한**: 중복 방 생성·동시 재전송을 직렬화하고, 재전송 시 발신자·방·내용을 검증합니다. WebSocket의 우회 구독과 브로커 직접 발행을 차단합니다.
- **화면 수정**: 모바일 목록 복귀, 가변 화면 높이, 줄바꿈, 한글 조합 중 Enter, 일반 HTTP 접속의 UUID 생성을 수정했습니다.

[변경·검증 상세 및 브라우저 테스트 실행법](docs/UPGRADE_2026-10-22.md)

## 추가 실행 점검

[추가 실행 점검 결과](docs/RECHECK_2026-10-22.md): 방별 초안·실패 메시지 복원, 화면 밖 메시지의 읽음 처리, 말투 편집 유지와 세션 만료 안내를 수정했습니다. Chrome에서 오류 주입을 포함한 13개 시나리오 묶음을 통과했습니다.
