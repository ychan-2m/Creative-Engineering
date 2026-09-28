# WebTalk 구현 설명

이 문서는 현재 소스 코드의 구조와 처리 방식을 설명합니다. 구현된 코드와 실제 테스트로 검증한 범위는 구분합니다.

## 서버 파일별 역할

아래 경로는 `src/main/java/com/example/webtalk/` 기준입니다.

| 파일·패키지 | 역할과 구현 방식 |
| --- | --- |
| `WebtalkApplication.java` | Spring Boot 애플리케이션 시작점 |
| `auth/AuthController.java` | 회원가입·로그인·로그아웃·현재 사용자 REST API, 로그인 결과를 세션에 저장 |
| `auth/SecurityConfig.java` | 공개 경로와 인증 필요 경로 구분, BCrypt와 세션 저장소 구성 |
| `auth/CustomUserDetailsService.java` | 로그인 아이디로 DB 사용자를 조회하여 인증에 제공 |
| `auth/WebtalkUserPrincipal.java` | 인증된 사용자의 ID·아이디·닉네임 보관 |
| `user/User.java`, `UserRepository.java` | 사용자 엔티티와 DB 조회 |
| `user/UserService.java`, `UserController.java` | 가입 처리, 아이디 중복 검사, 사용자 검색 |
| `chat/ChatRoom.java`, `ChatRoomMember.java`, `ChatRoomMemberId.java` | 대화방·구성원과 방 ID/사용자 ID 복합 키 표현 |
| `chat/ChatRoomRepository.java`, `ChatRoomMemberRepository.java` | 기존 1대1 방과 구성원 조회 |
| `chat/ChatRoomService.java`, `ChatRoomController.java` | 기존 방 반환 또는 새 방 생성, 상대·최근 메시지·안 읽은 수 조회 |
| `message/Message.java`, `MessageRepository.java` | 메시지 저장과 최근 기록·요청 ID 조회 |
| `message/MessageService.java` | 방 구성원 검사, 메시지 길이 검사, 중복 요청 조회, 저장과 최근 50개 기록 반환 |
| `message/ChatMessageController.java` | 대화 기록 REST API |
| `websocket/WebSocketConfig.java` | `/ws` 연결, 내장 메시지 브로커와 인터셉터 설정 |
| `websocket/WebSocketAuthInterceptor.java` | CONNECT 인증 여부 및 `/topic/chat/{roomId}` SUBSCRIBE 구성원 검사 |
| `websocket/ChatWebSocketController.java` | 메시지 저장 후 방 토픽에 발행, 개인 오류 큐로 오류 전달 |
| 각 기능의 `dto/` | 요청·응답 구조 정의, 일부 요청의 Bean Validation 규칙 선언 |
| `common/` | 공통 오류 응답과 예외를 HTTP 상태 코드로 변환 |

## 화면 파일별 역할

아래 경로는 `src/main/resources/static/` 기준입니다.

| 파일 | 역할 |
| --- | --- |
| `index.html` | 첫 접속 진입 페이지 |
| `signup.html`, `js/signup.js` | 회원가입 폼과 가입 API 호출 |
| `login.html`, `js/login.js` | 로그인 폼과 로그인 API 호출 |
| `chat.html` | 검색·대화 목록·채팅 영역, STOMP.js 로딩 |
| `js/api.js` | fetch 요청, 세션 쿠키 전달, JSON 응답과 오류 공통 처리 |
| `js/socket.js` | STOMP 연결, 방 구독, 발행, 재연결 관리 |
| `js/chat.js` | 사용자 검색, 방 선택, 기록 조회, 말풍선과 전송 상태·재시도 처리 |
| `css/app.css` | 폼·대화 목록·말풍선·상태 표시와 모바일 레이아웃 |

메시지 내용과 사용자 이름은 `textContent`로 표시합니다.
재시도는 같은 `clientMessageId`를 사용하고, 서버는 해당 ID로 이미 저장된 메시지를 찾아 반환합니다.
DB에도 해당 값의 UNIQUE 제약이 있습니다. 동시에 들어오는 중복 요청의 오류 처리와
기존 메시지의 발신자·방 일치 검증은 추가 보완이 필요합니다.

## REST API

회원가입과 로그인을 제외한 아래 API에는 로그인 세션이 필요합니다.

| 메서드 | 경로 | 용도 / 주요 입력 |
| --- | --- | --- |
| POST | `/api/auth/signup` | 회원가입: `loginId`, `nickname`, `password` |
| POST | `/api/auth/login` | 로그인: `loginId`, `password` |
| POST | `/api/auth/logout` | 세션 무효화 |
| GET | `/api/me` | 현재 사용자 조회 |
| GET | `/api/users?query=검색어` | 사용자 검색 |
| POST | `/api/chat-rooms/direct` | 1대1 방 조회·생성: `targetUserId` |
| GET | `/api/chat-rooms` | 참여한 방 목록 |
| GET | `/api/chat-rooms/{roomId}/messages` | 최근 메시지 최대 50개 |

## WebSocket 메시지

| 구분 | 경로 |
| --- | --- |
| 연결 | `/ws` |
| 클라이언트 전송 | `/app/chat.send` |
| 방 메시지 구독 | `/topic/chat/{roomId}` |
| 개인 오류 구독 | `/user/queue/errors` |

전송 본문 예시:

```json
{
  "roomId": 1,
  "clientMessageId": "123e4567-e89b-42d3-a456-426614174000",
  "content": "안녕하세요!"
}
```

메시지 내용은 앞뒤 공백 제거 후 1~500자로 제한합니다.
서버 응답에는 `messageId`, `roomId`, `senderId`, `senderNickname`, `content`,
`createdAt`, `clientMessageId`가 포함됩니다.
전송 실패 시 재시도 버튼이 표시되며, 연결 종료 시 1·2·4·4·4초 간격으로 재연결을 시도합니다.

## 데이터베이스

`src/main/resources/schema.sql`에서 테이블과 인덱스를 생성합니다.
JPA의 자동 스키마 생성은 사용하지 않습니다(`ddl-auto: none`).

| 테이블 | 저장 정보 |
| --- | --- |
| `users` | 로그인 아이디, 비밀번호 해시, 닉네임, 생성 시각 |
| `chat_rooms` | 방 유형과 생성 시각 |
| `chat_room_members` | 방과 사용자의 참여 관계, 참여 시각, 마지막 읽은 메시지 ID |
| `messages` | 방, 발신자, 클라이언트 요청 ID, 내용, 생성 시각 |

사용자는 구성원 테이블을 통해 여러 방에 참여하며, 각 메시지는 방과 발신자를 참조합니다.
`last_read_message_id` 컬럼은 있지만 이를 갱신하는 API는 아직 없습니다.
MySQL 등으로 전환하려면 JDBC 드라이버, 접속 설정과 SQL 문법의 호환성을 함께 조정해야 합니다.

## 수동 확인 항목

아래는 실행 후 확인할 항목이며, 이 문서 자체가 테스트 통과 기록은 아닙니다.

- [ ] 서로 다른 두 계정으로 회원가입·로그인한다.
- [ ] 중복 아이디 가입과 잘못된 비밀번호 로그인이 거부되는지 확인한다.
- [ ] 상대를 검색하고 같은 상대와 방을 다시 열었을 때 기존 방을 반환하는지 확인한다.
- [ ] 두 브라우저에서 같은 방을 열고 양방향 메시지를 확인한다.
- [ ] 새로고침 및 서버 재시작 후 저장된 대화를 확인한다.
- [ ] 빈 메시지와 500자를 넘는 메시지의 거부 처리를 확인한다.
- [ ] 연결을 끊어 실패 표시·재연결·재시도 동작을 확인한다.
- [ ] 제3의 계정에서 다른 방의 기록 조회와 구독이 차단되는지 확인한다.
- [ ] 로그아웃 후 인증이 필요한 API 접근이 거부되는지 확인한다.

자동 테스트는 `src/test/java/com/example/webtalk/WebtalkApplicationTests.java`의
컨텍스트 로딩 테스트 1개입니다. 테스트 설정은 파일 DB 대신 인메모리 H2를 사용합니다.
