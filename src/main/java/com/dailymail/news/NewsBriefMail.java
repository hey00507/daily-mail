package com.dailymail.news;

import com.dailymail.config.MailConfig;
import com.dailymail.core.ClaudeService;
import com.dailymail.core.MailContent;
import com.dailymail.core.MailModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class NewsBriefMail implements MailModule {

    private final RssService rssService;
    private final ClaudeService claudeService;
    private final MailConfig mailConfig;

    @Override
    public String name() {
        return "news-brief";
    }

    @Override
    public boolean isEnabled() {
        var config = mailConfig.modules().get(name());
        return config != null && config.enabled();
    }

    private static final List<String> CATEGORIES = List.of("정치", "경제", "IT");

    @Override
    public MailContent generate() {
        log.info("뉴스 수집 시작");

        Map<String, List<SummarizedNews>> newsMap = new java.util.LinkedHashMap<>();
        int total = 0;

        for (String category : CATEGORIES) {
            List<RssService.NewsItem> items = rssService.fetchByCategory(category, 5);
            log.info("[{}] 수집: {}건", category, items.size());
            List<SummarizedNews> summaries = summarize(items, category);
            newsMap.put(category, summaries);
            total += summaries.size();
        }

        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("MM/dd"));
        String subject = String.format("[News] %s — 뉴스 %d선", today, total);

        return new MailContent(subject, "news-brief", Map.of(
                "date", today,
                "newsMap", newsMap
        ));
    }

    private List<SummarizedNews> summarize(List<RssService.NewsItem> items, String category) {
        List<SummarizedNews> result = new ArrayList<>();
        for (var item : items) {
            try {
                String summary = claudeService.ask(buildSummaryPrompt(item));
                result.add(new SummarizedNews(
                        item.title(), summary.trim(), item.link(), item.source(), category
                ));
            } catch (Exception e) {
                log.warn("요약 실패 [{}]: {}", item.title(), e.getMessage());
                result.add(new SummarizedNews(
                        item.title(), "", item.link(), item.source(), category
                ));
            }
        }
        return result;
    }

    private String buildSummaryPrompt(RssService.NewsItem item) {
        return """
                다음 기사를 한 줄(30자 이내)로 요약해줘. 요약만 출력해.

                제목: %s
                내용: %s
                """.formatted(item.title(), item.description());
    }

    public record SummarizedNews(
            String title,
            String summary,
            String link,
            String source,
            String category
    ) {}
}
