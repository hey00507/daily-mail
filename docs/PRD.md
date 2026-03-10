# Daily Mail — PRD

## 한 줄 요약
게으른 개발자를 위한 모닝 브리핑. 매일 아침 뉴스 + CS 지식 + 오늘 일정을 Claude가 정리해서 메일로 보내준다. 바이브코딩으로 만들었다.

---

## 왜 만드는가
- 면접 준비를 따로 시간 내서 하기 어려움 → 매일 조금씩 메일로 받아보면 자연스럽게 축적
- 뉴스를 하나하나 찾아보기 귀찮음 → 핵심만 요약해서 아침에 확인
- Spring Boot 기반 개인 프로젝트 → 포트폴리오 + 실사용

---

## 기능 정의

### 1. News Brief — 뉴스 Top 10

**IT/개발/테크** — 5건
- 소스: Hacker News, GeekNews, TechCrunch, Verge 등
- 태그: `#tech`

**시사/경제** — 5건
- 소스: 조선비즈, 한경, 매경, 연합뉴스 RSS 등
- 태그: `#부동산` `#경제` `#정치`

**구성**: 제목 + Claude API 한 줄 요약 + 태그 + 원문 링크

### 2. CS Daily — 면접 지식 메일링

**목표**: 국내 IT 대기업 (네카라쿠배당토) 백엔드 면접 대비

| 분류 | 세부 주제 |
|------|-----------|
| OS | 프로세스/스레드, 메모리 관리, 스케줄링, 동기화, 데드락, 가상 메모리, 페이징 |
| Network | TCP/UDP, HTTP/HTTPS, DNS, 로드밸런싱, REST, WebSocket, OSI 7계층 |
| DB | 인덱스, 트랜잭션/ACID, 정규화, 쿼리 최적화, 레플리케이션, 샤딩, NoSQL vs RDBMS |
| Java/Spring | JVM 구조, GC, Spring IoC/AOP/MVC, JPA/Hibernate, 트랜잭션 전파, Bean 스코프 |
| 자료구조/알고리즘 | 시간복잡도, 트리, 그래프, 해시, 정렬, DP, 그리디 |
| 디자인패턴 | 싱글톤, 팩토리, 전략, 옵저버, 프록시, 템플릿 메서드 |
| 인프라 | Docker, Kubernetes, CI/CD, 모니터링, 로깅, 무중단 배포 |
| 아키텍처 | MSA vs 모놀리식, 이벤트 드리븐, CQRS, 캐싱 전략, 메시지 큐, API Gateway |

**운영 방식**:
- 매일 아침 카테고리 중 랜덤 1개 주제 선택
- Claude API (Haiku)가 콘텐츠 생성: 질문 → 핵심 답변 → 꼬리질문 2~3개 → 깊이 있는 설명
- 발송 이력으로 중복 방지
- 별도 요청 없으면 자동 랜덤. 카테고리 집중/주제 변경은 요청 시 config 수정

### 3. Today Brief — 오늘의 일정
- Google Calendar API로 당일 이벤트 조회
- 일정이 있을 때만 발송 (없으면 스킵)
- 시간순 정리 + 특이사항 강조 (시작 임박, 종일 일정 등)

### 4. 발송 스케줄

| 시간 (KST) | 메일 | 조건 |
|------------|------|------|
| 07:30 | News Brief | 매일 |
| 07:40 | CS Daily | 매일 |
| 07:50 | Today Brief | 일정 있을 때만 |

- GitHub Actions cron으로 3회 분리 실행
- HTML 포맷 (Thymeleaf 템플릿, 모바일 대응)
- 수신: 본인 Gmail

---

## 아키텍처

```
GitHub Actions cron
  ├── 07:30 KST → News Brief (RSS + Claude 요약)
  ├── 07:40 KST → CS Daily (Claude 생성)
  └── 07:50 KST → Today Brief (Google Calendar API)
        ↓
Spring Boot CLI 배치 (web-application-type: none)
  CommandLineRunner 실행 → 메일 발송 → JVM 종료
        ↓
Gmail 수신 + 발송 이력 Git commit & push
```

### 설계 결정

| 결정 | 이유 |
|------|------|
| Spring Boot CLI 배치 | 서버 안 띄움. CommandLineRunner 실행 후 JVM 종료. 포트폴리오 가치. |
| GitHub Actions cron | Mac 상태 무관, 항상 실행. 무료 범위 내. 별도 인프라 불필요. |
| Claude API (Haiku) | CS 콘텐츠 수동 작성은 비현실적. 뉴스 요약 없으면 가치 없음. 하루 ~10원. |
| JSON + Git 커밋 | DB 없이 이력 관리. Git이 버전 관리. GitHub에서 바로 확인 가능. |
| Spring Mail + Thymeleaf | Spring 생태계 내에서 완결. 추후 웹 확장 시 Thymeleaf 재활용. |

### 참고 프로젝트

| 프로젝트 | 참고한 점 |
|----------|-----------|
| [claude-schedule](https://github.com/jackson-hong/claude-schedule) | 빌드 히스토리 패턴, 모듈 구조 |
| [apt-price-tracker](https://github.com/saechimdaeki/apt-price-tracker) | Spring Boot CLI 배치 패턴, GitHub Actions cron, 데이터 Git 커밋 |

---

## AI 활용

| 용도 | 모델 | 호출 | 비용 |
|------|------|------|------|
| CS 주제 생성 | Haiku | 하루 1회 | ~1원/건 |
| 뉴스 한 줄 요약 | Haiku | 하루 10회 | ~10원/일 |

### CS 프롬프트
```
카테고리: {category}
주제: {topic}

IT 대기업 백엔드 개발자 면접 기준으로 다음을 작성해줘:
1. 면접 질문 1개
2. 핵심 답변 (3~5문장)
3. 꼬리질문 2~3개와 각각의 답변
4. 깊이 있는 설명 (실무 관점 포함)
```

### 뉴스 요약 프롬프트
```
다음 기사를 한 줄(30자 이내)로 요약해줘:
{article_title}
{article_description}
```

---

## 운영 방식

설정 변경은 Claude에게 요청 → 코드/설정 수정 → 다음 발송부터 반영.

| 요청 예시 | 반영 방식 |
|----------|-----------|
| "내일부터 Network 집중으로" | config에서 카테고리 가중치 변경 |
| "뉴스에 AI 태그 추가해줘" | RSS 소스 + 태그 추가 |
| "주말엔 안 보내줘" | GitHub Actions cron 수정 |
| "Docker vs K8s 비교해서 보내줘" | 다음 CS 주제 직접 지정 |

---

## 확장 설계

### MailModule 인터페이스
```java
public interface MailModule {
    String name();
    boolean isEnabled();
    MailContent generate();
}
```

새 메일 추가 = `MailModule` 구현체 1개 + yml 설정 추가.

### 확장 후보
- 주간 회고 메일
- 채용 공고 모니터링
- GitHub 트렌딩 요약
- 개인 KPI/목표 리마인더

### 웹 대시보드 전환
필요 시 `web-application-type: none` 제거 → 같은 프로젝트에서 웹 활성화.

---

## 기술 스택

| 구분 | 기술 |
|------|------|
| 언어 | Java 21 |
| 프레임워크 | Spring Boot 4 (`web-application-type: none`) |
| 빌드 | Gradle (Kotlin DSL) |
| AI | Claude API (Haiku) — Anthropic Java SDK 또는 WebClient |
| 캘린더 | Google Calendar API (OAuth 2.0) |
| 뉴스 수집 | RSS 파싱 (WebClient) |
| 메일 | Spring Mail + Thymeleaf |
| 데이터 | JSON 파일 + Git 커밋 |
| 스케줄링 | GitHub Actions cron (07:30, 07:40, 07:50 KST) |
| CI/CD | GitHub Actions |

---

## 메일 포맷

### News Brief
```
제목: [News] 03/11 — 뉴스 10선

📰 Tech
1. #tech  [제목] — Claude 요약 (링크)
2. ...

📰 시사/경제
1. #경제   [제목] — Claude 요약 (링크)
2. #부동산  ...
3. #정치   ...
```

### CS Daily
```
제목: [CS] 03/11 — OS: 프로세스 vs 스레드

Q. 프로세스와 스레드의 차이는?
A. (Claude 생성)

🔄 꼬리질문
1. 멀티프로세스 vs 멀티스레드 장단점?
2. 컨텍스트 스위칭 비용이 왜 다른가?
3. 스레드 세이프하게 만드는 방법은?
```

### Today Brief
```
제목: [Today] 03/11 — 오늘 일정 2건

📅 오늘의 일정
09:00 의성마늘 프로젝트 회의
14:00 서울하프마라톤 접수 🏃
```

---

## 로드맵

| 단계 | 내용 | 기간 |
|------|------|------|
| **Phase 1** | Spring Boot 배치 + Claude API + GitHub Actions | 2~3일 |
| **Phase 2** | CS 카테고리 확충 + 프롬프트 튜닝 + 메일 디자인 | 1주 |
| **Phase 3** | 메일 모듈 확장 (주간 회고, 채용 공고 등) | 필요 시 |
| **Phase 4** | 웹 대시보드 (발송 이력, 설정 관리) | 필요 시 |
