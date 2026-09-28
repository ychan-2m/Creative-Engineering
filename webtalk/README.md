# WebTalk

창의공학설계 프로젝트로 만든 **브라우저 기반 1대1 실시간 메신저 프로토타입**입니다.
회원가입과 로그인 후 사용자를 검색해 대화방을 만들고, WebSocket으로 메시지를 주고받습니다.
대화 내용은 로컬 H2 데이터베이스에 저장합니다.

## 구현한 기능

| 기능 | 구현 방식 |
| --- | --- |
| 회원가입 | 아이디·닉네임·비밀번호 검증, 중복 아이디 검사, BCrypt 비밀번호 해시 저장 |
| 로그인·로그아웃 | Spring Security 인증 결과를 HTTP 세션에 저장하고 로그아웃 시 세션 무효화 |
| 사용자 검색 | 아이디·닉네임 검색, 본인 제외 |
| 1대1 대화방 | 두 사용자의 기존 방을 조회하고 없으면 방과 구성원 생성 |
| 실시간 채팅 | WebSocket + STOMP로 메시지를 전송하고 방 구독자에게 전달 |
| 대화 기록 | 방 구성원 확인 후 최근 50개 메시지를 시간순으로 조회 |
| 전송 상태·재시도 | 전송 중 표시, 5초 내 확인이 없으면 실패 표시, 같은 요청 ID로 재시도 |
| 연결 상태 | 연결 상태 표시, 연결 종료 시 최대 5회 재연결 시도, 수동 재연결 |
| 기본 화면 | 회원가입·로그인·채팅 화면, 모바일 폭에 대응하는 CSS |

안 읽은 수 응답과 배지는 구현되어 있지만, 읽음 처리 API가 없어 정확한 미확인 메시지 수를 나타내지 않습니다.

## 기술 스택

| 구분 | 사용 기술 |
| --- | --- |
| 언어 | Java 17, JavaScript |
| 서버 | Spring Boot 4.1.1, Spring MVC, Spring Security, Spring Data JPA, Validation |
| 화면 | HTML, CSS, 순수 JavaScript |
| 실시간 통신 | Spring WebSocket, STOMP, 브라우저용 STOMP.js 7.0.0 |
| 데이터베이스 | H2 파일 DB, SQL 스키마 직접 관리 |
| 빌드 | Maven Wrapper |

버전은 저장소의 `pom.xml`과 `chat.html` 기준입니다.

## 실행 방법

JDK 17 이상이 필요합니다. 처음 실행할 때 Maven과 의존성을 다운로드하며,
채팅 화면은 CDN에서 STOMP.js를 불러오므로 인터넷 연결이 필요합니다.

프로젝트 루트에서 실행합니다.

```bash
# macOS / Linux
./mvnw spring-boot:run
```

```powershell
# Windows PowerShell
.\mvnw.cmd spring-boot:run
```

브라우저에서 <http://localhost:8080>에 접속합니다.
일반 창과 시크릿 창에서 서로 다른 계정으로 로그인하면 두 사용자 간 채팅을 시연할 수 있습니다.
아이디는 영문·숫자·`-`·`_`로 3~40자, 비밀번호는 8~100자로 입력합니다.

기본 포트는 `8080`이며 데이터는 `data/`에 생성됩니다. 서버를 재시작해도 DB 파일이 유지되면
대화 기록이 남습니다. `data/`는 Git 업로드 대상에서 제외됩니다.

## 프로젝트 구조

```text
webtalk/
├── README.md                 # 프로젝트 소개와 실행 방법
├── docs/
│   ├── IMPLEMENTATION.md     # 무엇을 어떻게 구현했는지 설명
│   └── GITHUB_UPLOAD.md      # 업로드 대상과 GitHub 등록 방법
├── pom.xml                   # 의존성과 빌드 설정
├── mvnw / mvnw.cmd           # 운영체제별 Maven 실행 스크립트
├── .mvn/wrapper/             # Maven Wrapper 설정
├── src/main/java/com/example/webtalk/
│   ├── WebtalkApplication.java
│   ├── auth/                # 세션 인증과 접근 제어
│   ├── user/                # 사용자 저장·검색
│   ├── chat/                # 대화방과 구성원 관리
│   ├── message/             # 메시지 저장·기록 조회
│   ├── websocket/           # STOMP 연결·구독·전송
│   └── common/              # 공통 오류 응답과 예외 처리
├── src/main/resources/
│   ├── application.yml      # 서버·DB 설정
│   ├── schema.sql           # 테이블과 인덱스 생성
│   └── static/              # HTML, CSS, JavaScript
└── src/test/                # 애플리케이션 컨텍스트 로딩 테스트
```

## 동작 흐름

1. 회원가입 정보를 검증하고 비밀번호를 해시하여 저장합니다.
2. 로그인 성공 시 세션을 생성하고 REST 요청과 WebSocket 연결에서 인증 상태를 공유합니다.
3. 사용자를 검색해 기존 1대1 방을 열거나 새 방을 생성합니다.
4. 방의 메시지 토픽을 구독하고 REST API로 최근 대화 기록을 불러옵니다.
5. 메시지 전송 시 서버가 방 구성원 여부와 내용을 확인하고 DB에 저장합니다.
6. 저장 트랜잭션이 끝나면 구독자에게 메시지를 전달합니다.
7. 발신 화면은 `clientMessageId`로 서버 응답과 전송 중 말풍선을 연결해 상태를 갱신합니다.

자세한 파일별 역할, API, DB 구성은 [구현 설명](docs/IMPLEMENTATION.md)에 정리했습니다.

## 테스트

```bash
./mvnw test
```

현재 자동 테스트는 `contextLoads` 1개로, 인메모리 H2를 사용해 애플리케이션 컨텍스트가
로딩되는지 확인합니다. 채팅 송수신과 권한 검사를 모두 검증하는 기능 테스트는 아닙니다.
수동 확인 항목은 [구현 설명](docs/IMPLEMENTATION.md#수동-확인-항목)을 참고하세요.

## 현재 한계와 후속 작업

- 읽음 처리 API 및 안 읽은 수 계산 개선: 현재 본인이 보낸 메시지도 집계에 포함됩니다.
- 최근 50개보다 오래된 대화의 페이지 단위 조회 UI는 없습니다.
- 현재 선택한 방을 구독하며, 다른 방의 실시간 알림은 구현하지 않았습니다.
- 재연결 후 누락 메시지 보충, 동시 방 생성·중복 전송 처리 강화가 필요합니다.
- 단체 채팅, 파일 전송, 푸시 알림, 다중 서버 지원은 구현하지 않았습니다.
- 개발 설정으로 H2 콘솔이 활성화되어 있고 API의 CSRF 검사가 제외되어 있습니다.
  외부 배포 시 인증·CSRF·STOMP 목적지 권한과 콘솔 설정을 점검해야 합니다.
- WebSocket 허용 Origin은 `localhost:8080`과 `127.0.0.1:8080`입니다.
  외부 주소로 실행하려면 `WebSocketConfig` 설정을 조정해야 합니다.

## GitHub 업로드

소스 코드, 문서, Maven 설정을 올리고 로컬 DB·빌드 결과물·개인 환경 파일은 제외합니다.
[GitHub 업로드 안내](docs/GITHUB_UPLOAD.md)에 명령어와 커밋 메시지를 정리했습니다.
