# CLAUDE.md

이 프로젝트는 Claude Code와 바이브코딩으로 만들어졌다.
코드를 수정하거나 기능을 추가할 때 이 문서를 참고한다.

## 프로젝트 개요

게으른 개발자를 위한 모닝 브리핑 시스템.
매일 아침 3통의 메일을 보낸다:
- 07:30 News Brief — Tech 5건 + 시사/경제 5건 (RSS + Claude 요약)
- 07:40 CS Daily — 면접 지식 1개 (Claude 생성)
- 07:50 Today Brief — Google Calendar 일정 요약 (있을 때만)

## 아키텍처

Spring Boot를 서버가 아닌 CLI 배치로 사용한다.
`spring.main.web-application-type: none`으로 웹서버를 띄우지 않고,
`CommandLineRunner`로 실행 → 메일 발송 → JVM 종료.

GitHub Actions cron이 매일 아침 3회 실행하고,
`--mail.module` CLI arg로 어떤 메일을 보낼지 결정한다.

## 기술 스택

- Java 21, Spring Boot 4, Gradle (Kotlin DSL)
- Claude API (Haiku) — CS 콘텐츠 생성 + 뉴스 요약
- Google Calendar API — 일정 조회
- Spring Mail + Thymeleaf — 메일 발송
- GitHub Actions — 스케줄링 + CI/CD

## 패키지 구조

```
com.dailymail
├── runner/MailRunner           CommandLineRunner. mail.module arg에 따라 해당 모듈 실행.
├── mail/MailModule             공통 인터페이스. name(), isEnabled(), generate().
├── mail/NewsBriefMail          RSS 수집 → Claude 요약 → 메일 콘텐츠 생성
├── mail/CsDailyMail            카테고리 랜덤 선택 → Claude로 CS 콘텐츠 생성
├── mail/TodayBriefMail         Google Calendar API → 당일 일정 조회 → 일정 없으면 스킵
├── service/ClaudeService       Claude API 호출 (Haiku). 프롬프트 조립 + 응답 파싱.
├── service/RssService          RSS 피드 수집. tech + 시사/경제 소스 분리.
├── service/CalendarService     Google Calendar API. OAuth 2.0 인증.
├── service/MailService         Spring Mail로 발송. Thymeleaf 템플릿 렌더링.
└── config/MailConfig           모듈별 설정 바인딩 (application.yml)
```

## 설정

환경변수 (GitHub Secrets):
- `CLAUDE_API_KEY` — Anthropic API key
- `GMAIL_ADDRESS` — 발송/수신 Gmail
- `GMAIL_APP_PASSWORD` — Gmail 앱 비밀번호
- `GOOGLE_CREDENTIALS` — Google Calendar OAuth JSON (Base64)

## 데이터

- `data/history.json` — 발송 이력. 매 실행 후 Git commit & push.
- DB 없음. JSON + Git으로 이력 관리.

## 실행

```bash
# 전체 모듈
./gradlew bootRun

# 특정 모듈만
./gradlew bootRun --args="--mail.module=news-brief"
./gradlew bootRun --args="--mail.module=cs-daily"
./gradlew bootRun --args="--mail.module=today-brief"
```

## 새 메일 모듈 추가 방법

1. `MailModule` 인터페이스를 구현하는 클래스 생성
2. `application.yml`의 `mail.modules`에 설정 추가
3. `MailRunner`에서 자동으로 인식됨 (Spring Bean 스캔)

## 코딩 컨벤션

- 서버를 띄우지 않는다. `web-application-type: none` 유지.
- 외부 API 호출은 WebClient 사용.
- 메일 템플릿은 `src/main/resources/templates/`에 Thymeleaf로 작성.
- 설정값은 하드코딩하지 않고 `application.yml` + 환경변수.
- 발송 이력은 `data/history.json`에 append.

## 주의사항

- GitHub Actions 무료 범위: public repo는 무제한, private은 월 2,000분.
- Claude API 비용: Haiku 기준 하루 ~10원 (11회 호출).
- Google Calendar API: OAuth 토큰 갱신 로직 필요.
- `today-brief`는 일정 없으면 메일을 보내지 않는다 (`skip-if-empty: true`).
