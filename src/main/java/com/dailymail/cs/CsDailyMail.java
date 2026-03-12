package com.dailymail.cs;

import com.dailymail.config.MailConfig;
import com.dailymail.core.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class CsDailyMail implements MailModule {

    private static final Map<String, List<String>> CATEGORIES = new LinkedHashMap<>();

    static {
        CATEGORIES.put("OS", List.of(
                "프로세스 vs 스레드", "메모리 관리", "CPU 스케줄링", "동기화와 뮤텍스/세마포어",
                "데드락", "가상 메모리", "페이징과 세그멘테이션", "컨텍스트 스위칭"
        ));
        CATEGORIES.put("Network", List.of(
                "TCP vs UDP", "HTTP/HTTPS", "DNS 동작 원리", "로드밸런싱",
                "REST API", "WebSocket", "OSI 7계층", "TCP 3-way handshake"
        ));
        CATEGORIES.put("DB", List.of(
                "인덱스 동작 원리", "트랜잭션과 ACID", "정규화", "쿼리 최적화",
                "레플리케이션", "샤딩", "NoSQL vs RDBMS", "MVCC"
        ));
        CATEGORIES.put("Java/Spring", List.of(
                "JVM 구조와 메모리", "가비지 컬렉션", "Spring IoC/DI", "Spring AOP",
                "JPA와 영속성 컨텍스트", "트랜잭션 전파", "Bean 스코프와 라이프사이클", "Spring MVC 동작 원리"
        ));
        CATEGORIES.put("자료구조/알고리즘", List.of(
                "시간복잡도와 공간복잡도", "트리와 이진탐색트리", "그래프 탐색 BFS/DFS",
                "해시테이블", "정렬 알고리즘 비교", "동적 프로그래밍", "그리디 알고리즘"
        ));
        CATEGORIES.put("디자인패턴", List.of(
                "싱글톤 패턴", "팩토리 패턴", "전략 패턴", "옵저버 패턴",
                "프록시 패턴", "템플릿 메서드 패턴"
        ));
        CATEGORIES.put("인프라", List.of(
                "Docker 컨테이너", "Kubernetes 기초", "CI/CD 파이프라인",
                "모니터링과 로깅", "무중단 배포 전략"
        ));
        CATEGORIES.put("아키텍처", List.of(
                "MSA vs 모놀리식", "이벤트 드리븐 아키텍처", "CQRS 패턴",
                "캐싱 전략", "메시지 큐", "API Gateway"
        ));
    }

    private final ClaudeService claudeService;
    private final HistoryService historyService;
    private final MailConfig mailConfig;

    @Override
    public String name() {
        return "cs-daily";
    }

    @Override
    public boolean isEnabled() {
        var config = mailConfig.modules().get(name());
        return config != null && config.enabled();
    }

    @Override
    public MailContent generate() {
        List<String> sentTopics = historyService.getSentTopics(name());
        TopicSelection selection = pickRandomTopic(sentTopics);

        log.info("CS Daily 주제 선택: [{}] {}", selection.category, selection.topic);

        String prompt = buildPrompt(selection.category, selection.topic);
        String response = claudeService.ask(prompt);

        historyService.append(name(), selection.category + ": " + selection.topic);

        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("MM/dd"));
        String subject = String.format("[CS] %s — %s: %s", today, selection.category, selection.topic);

        String htmlContent = markdownToHtml(response);

        return new MailContent(subject, "cs-daily", Map.of(
                "category", selection.category,
                "topic", selection.topic,
                "content", htmlContent,
                "contentMarkdown", response,
                "date", today
        ));
    }

    private TopicSelection pickRandomTopic(List<String> sentTopics) {
        List<TopicSelection> available = new ArrayList<>();

        for (var entry : CATEGORIES.entrySet()) {
            for (String topic : entry.getValue()) {
                String key = entry.getKey() + ": " + topic;
                if (!sentTopics.contains(key)) {
                    available.add(new TopicSelection(entry.getKey(), topic));
                }
            }
        }

        if (available.isEmpty()) {
            log.info("모든 주제를 소진했습니다. 이력을 초기화합니다.");
            available = CATEGORIES.entrySet().stream()
                    .flatMap(e -> e.getValue().stream().map(t -> new TopicSelection(e.getKey(), t)))
                    .toList();
        }

        return available.get(new Random().nextInt(available.size()));
    }

    private String buildPrompt(String category, String topic) {
        return """
                카테고리: %s
                주제: %s

                IT 대기업 백엔드 개발자 면접 기준으로 다음을 작성해줘:
                1. 면접 질문 1개
                2. 핵심 답변 (3~5문장)
                3. 꼬리질문 2~3개와 각각의 답변
                4. 깊이 있는 설명 (실무 관점 포함)

                마크다운 형식으로 작성하되, 각 섹션을 ## 헤더로 구분해줘.
                """.formatted(category, topic);
    }

    private String markdownToHtml(String markdown) {
        Parser parser = Parser.builder().build();
        HtmlRenderer renderer = HtmlRenderer.builder().build();
        return renderer.render(parser.parse(markdown));
    }

    private record TopicSelection(String category, String topic) {}
}
