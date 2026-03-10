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

    @Override
    public MailContent generate() {
        log.info("뉴스 수집 시작");

        List<RssService.NewsItem> techNews = rssService.fetchTech(5);
        List<RssService.NewsItem> generalNews = rssService.fetchGeneral(5);

        log.info("수집 완료 — tech: {}건, general: {}건", techNews.size(), generalNews.size());

        List<SummarizedNews> techSummaries = summarize(techNews, "tech");
        List<SummarizedNews> generalSummaries = summarize(generalNews, "시사/경제");

        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("MM/dd"));
        String subject = String.format("[News] %s — 뉴스 %d선",
                today, techSummaries.size() + generalSummaries.size());

        return new MailContent(subject, "news-brief", Map.of(
                "date", today,
                "techNews", techSummaries,
                "generalNews", generalSummaries
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
