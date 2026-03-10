# Daily Mail

아침에 눈 뜨면 메일함에 오늘 알아야 할 것들이 정리되어 있다.
뉴스 찾아볼 필요 없고, CS 공부 따로 안 해도 되고, 일정도 알아서 알려준다.
게으른 개발자를 위한 모닝 브리핑 시스템.

Claude와 바이브코딩으로 만들었다. 기획부터 PRD 작성, 아키텍처 설계, 코드 생성까지
Claude Code와 대화하면서 진행했고, 이 README도 그렇게 만들어졌다.

## 매일 아침 오는 메일 3통

| 시간 | 메일 | 내용 |
|------|------|------|
| 07:30 | News Brief | Tech 뉴스 5건 + 시사/경제 뉴스 5건 (Claude가 한 줄 요약) |
| 07:40 | CS Daily | 면접 질문 1개 + 핵심 답변 + 꼬리질문 (Claude가 생성) |
| 07:50 | Today Brief | 오늘 Google Calendar 일정 요약 (있을 때만) |

아무것도 안 해도 된다. GitHub Actions가 매일 돌리고, Claude가 콘텐츠를 만들고, Spring Mail이 보낸다.

## 동작 방식

```
GitHub Actions cron
  ├── 07:30 KST → News Brief (RSS + Claude 요약)
  ├── 07:40 KST → CS Daily (Claude 생성)
  └── 07:50 KST → Today Brief (Google Calendar API)
        ↓
  Spring Boot CLI 배치 (서버 아님, CommandLineRunner 실행 후 종료)
        ↓
  Gmail 수신
        ↓
  발송 이력 Git commit & push
```

## 기능

### News Brief — 뉴스 10선
- Tech 5건: Hacker News, GeekNews, TechCrunch, Verge
- 시사/경제 5건: 조선비즈, 한경, 매경, 연합뉴스
- 태그: `#tech` `#부동산` `#경제` `#정치`
- Claude가 각 기사를 한 줄로 요약

### CS Daily — 면접 지식
- 국내 IT 대기업 백엔드 면접 대비
- 8개 카테고리: OS, Network, DB, Java/Spring, 자료구조/알고리즘, 디자인패턴, 인프라, 아키텍처
- 매일 랜덤 1개 주제. Claude가 질문 → 답변 → 꼬리질문 → 심화 설명 생성
- 이전에 보낸 주제는 자동으로 제외

### Today Brief — 오늘의 일정
- Google Calendar API로 당일 이벤트 조회
- 일정이 있으면 시간순 정리해서 발송. 없으면 메일 안 감.
- 특이사항 (시작 임박, 종일 일정 등) 강조

## 기술 스택

| 구분 | 기술 |
|------|------|
| Java 21 | LTS |
| Spring Boot 4 | CLI 배치 (`web-application-type: none`) |
| Gradle | Kotlin DSL |
| AI | Claude API (Haiku) — 콘텐츠 생성 + 뉴스 요약 |
| 캘린더 | Google Calendar API (OAuth 2.0) |
| 메일 | Spring Mail + Thymeleaf |
| 뉴스 | RSS 파싱 (WebClient) |
| 데이터 | JSON + Git 커밋 |
| CI/CD | GitHub Actions cron |

## 프로젝트 구조

도메인(기능) 기준으로 패키지를 분리한다. 모듈 추가 = 패키지 1개.

```
src/main/java/com/dailymail/
├── core/                            # 공통 (메일 발송, Claude API, 실행기, 이력)
│   ├── MailModule.java
│   ├── MailRunner.java
│   ├── MailService.java
│   ├── ClaudeService.java
│   └── HistoryService.java
├── news/                            # News Brief
│   ├── NewsBriefMail.java
│   └── RssService.java
├── cs/                              # CS Daily
│   └── CsDailyMail.java
├── today/                           # Today Brief
│   ├── TodayBriefMail.java
│   └── CalendarService.java
├── config/
│   └── MailConfig.java
└── DailyMailApplication.java
```

## 로컬 환경 세팅

### 1. CLAUDE_API_KEY — Anthropic API 키

CS 콘텐츠 생성 + 뉴스 요약에 사용. Haiku 모델 기준 하루 ~10원.

1. [Anthropic Console](https://console.anthropic.com/) 접속
2. Settings → API Keys → **Create Key**
3. 생성된 `sk-ant-...` 키를 복사

### 2. GMAIL_ADDRESS / GMAIL_APP_PASSWORD — Gmail 발송

일반 Gmail 비밀번호가 아닌 **앱 비밀번호**가 필요하다.

1. [Google 계정 보안](https://myaccount.google.com/security) 접속
2. **2단계 인증** 활성화 (이미 활성화되어 있으면 스킵)
3. [앱 비밀번호 생성](https://myaccount.google.com/apppasswords) 페이지 접속
4. 앱 이름에 `daily-mail` 입력 → **만들기**
5. 생성된 16자리 비밀번호를 복사 (예: `abcd efgh ijkl mnop`)

- `GMAIL_ADDRESS`: 발송 **및** 수신에 사용할 Gmail (예: `your@gmail.com`)
- `GMAIL_APP_PASSWORD`: 위에서 생성한 16자리 앱 비밀번호

### 3. GOOGLE_CREDENTIALS — Google Calendar API (선택)

Today Brief 모듈에서만 사용. 설정하지 않으면 일정 메일만 스킵되고 나머지는 정상 동작.

1. [Google Cloud Console](https://console.cloud.google.com/) 접속
2. 새 프로젝트 생성 (또는 기존 프로젝트 선택)
3. **API 및 서비스** → **라이브러리** → "Google Calendar API" 검색 → **사용 설정**
4. **API 및 서비스** → **사용자 인증 정보** → **서비스 계정 만들기**
   - 이름: `daily-mail`
   - 역할: 없음 (기본)
   - 완료
5. 생성된 서비스 계정 클릭 → **키** 탭 → **키 추가** → **JSON**
   - JSON 파일이 다운로드됨
6. Google Calendar에서 서비스 계정에 캘린더 공유:
   - [Google Calendar](https://calendar.google.com/) → 설정 → 내 캘린더 → **특정 사용자와 공유**
   - 서비스 계정 이메일 추가 (예: `daily-mail@project-id.iam.gserviceaccount.com`)
   - 권한: **일정의 세부정보 보기**
7. JSON 파일을 Base64로 인코딩:
   ```bash
   base64 -i path/to/credentials.json | tr -d '\n'
   ```
8. 출력된 문자열을 `GOOGLE_CREDENTIALS`에 설정

### 환경변수 설정

```bash
# ~/.zshrc 또는 ~/.bashrc에 추가 (로컬 개발용)
export CLAUDE_API_KEY=sk-ant-api03-...
export GMAIL_ADDRESS=your@gmail.com
export GMAIL_APP_PASSWORD="abcd efgh ijkl mnop"
export GOOGLE_CREDENTIALS=eyJ0eXBlIjoic2VydmljZV9hY2NvdW50Ii...  # 선택
```

또는 실행 시 직접 지정:
```bash
CLAUDE_API_KEY=... GMAIL_ADDRESS=... GMAIL_APP_PASSWORD=... \
  ./gradlew bootRun --args="--mail.module=cs-daily"
```

## 실행

```bash
# 특정 모듈만 실행
./gradlew bootRun --args="--mail.module=news-brief"
./gradlew bootRun --args="--mail.module=cs-daily"
./gradlew bootRun --args="--mail.module=today-brief"

# 전체 모듈 실행
./gradlew bootRun

# 테스트 (커버리지 리포트 자동 생성)
./gradlew test

# 커버리지 리포트 확인
open build/reports/jacoco/test/html/index.html
```

## 확장

`MailModule` 인터페이스를 구현하면 새 메일을 추가할 수 있다.

```java
public interface MailModule {
    String name();
    boolean isEnabled();
    MailContent generate();
}
```

확장 후보: 주간 회고, 채용 공고 모니터링, GitHub 트렌딩

웹 대시보드가 필요해지면 `web-application-type: none`을 제거하고 같은 프로젝트에서 전환.

## Vibe Coding

이 프로젝트는 Claude Code와 대화하면서 만들었다.

- 기획: "매일 아침 CS 지식이랑 뉴스를 메일로 받고 싶어"
- 기술 선택: Python vs TS vs Spring Boot 비교 → Spring Boot CLI 배치 확정
- 아키텍처: claude-schedule, apt-price-tracker 분석 → GitHub Actions + Spring Boot 조합
- 구현: Claude Code가 코드 생성, 설정 작성, workflow 구성

바이브코딩의 핵심은 "뭘 만들지"만 알면 된다는 것. 나머지는 AI가 한다.
