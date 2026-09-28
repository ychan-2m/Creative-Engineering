# GitHub 업로드 안내

## 올릴 파일

| 대상 | 포함 이유 |
| --- | --- |
| `src/` | 서버·화면 소스, DB 스키마, 설정, 테스트 |
| `pom.xml` | 동일한 의존성과 빌드 구성 재현 |
| `mvnw`, `mvnw.cmd`, `.mvn/` | Maven Wrapper 실행 환경 |
| `README.md`, `docs/` | 프로젝트 소개, 구현 내용과 사용 방법 |
| `.gitignore`, `.gitattributes` | 업로드 제외 규칙과 파일 속성 |

`target/`, `data/`, IDE 설정, 로그, `.env` 등 개인 환경 파일은 `.gitignore`로 제외합니다.
`HELP.md`는 초기 생성된 Spring 도움말이므로 기존 제외 설정을 유지합니다.
숨김 폴더 `.mvn/`도 필요하므로 Git 명령을 통한 업로드를 권장합니다.

## 현재 저장소 구성

이 프로젝트는 `ychan-2m/Creative-Engineering` 저장소의 `webtalk/` 폴더에 포함되어 있습니다.
추가 변경은 저장소 루트에서 커밋하고 push하면 됩니다. `webtalk/` 안에 별도 Git 저장소를 만들 필요가 없습니다.

## 별도 빈 저장소에 처음 연결하는 경우

아래는 이 프로젝트만 별도 저장소로 분리할 때 사용하는 참고 절차입니다.
GitHub에서 **비어 있는 `webtalk` 저장소**를 만든 후 아래 명령을 프로젝트 루트에서 실행합니다.
`YOUR_GITHUB_ID`는 본인 GitHub 계정명으로 바꿉니다.

```bash
git init -b main
git add .gitignore .gitattributes .mvn mvnw mvnw.cmd pom.xml src README.md docs
git diff --cached --stat
git status --short
git commit -m "feat: WebTalk 1대1 실시간 메신저 구현 및 문서 정리"
git remote add origin https://github.com/YOUR_GITHUB_ID/webtalk.git
git push -u origin main
```

이미 파일이나 커밋이 있는 GitHub 저장소라면 위의 최초 연결 절차 대신 해당 저장소를 별도 경로에
clone한 뒤 변경 파일을 반영하고 차이를 확인하여 커밋합니다. 기존 이력을 덮어쓰는 강제 push는 필요하지 않습니다.

## GitHub 저장소 설명 예시

> Spring Boot와 WebSocket/STOMP를 활용한 1대1 실시간 웹 메신저. 세션 인증, 사용자 검색, 대화 기록 저장, 메시지 재시도 기능을 구현한 창의공학설계 프로젝트.

## 최초 등록 내용 요약

- Spring Security 세션 인증과 BCrypt 기반 회원가입 구현
- 사용자 검색 및 기존 1대1 대화방 조회·생성 구현
- WebSocket/STOMP 실시간 메시지 송수신과 H2 대화 기록 저장
- 메시지 전송 상태, 재시도, 연결 상태 표시와 재연결 처리
- HTML/CSS/JavaScript 화면 및 REST API 공통 처리 구성
- 실행 방법, 파일별 구현 방식, 현재 한계와 테스트 범위 문서화

현재 정리 작업은 문서와 업로드 제외 규칙을 보완한 작업입니다.
위 기능 목록은 기존 프로젝트 전체의 구현 내용이며, 이번에 새로 개발한 기능 목록은 아닙니다.
