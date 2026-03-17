package com.dailymail.news;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 RSS 피드 연결 테스트 (네트워크 필요).
 * CI에서는 @Tag("live")로 제외 가능.
 */
@Tag("live")
class RssLiveFeedTest {

    private final RssService service = new RssService(
            WebClient.builder()
                    .defaultHeader("User-Agent", "Mozilla/5.0 (compatible; DailyMail/1.0)")
                    .build(),
            RssService.DEFAULT_FEEDS
    );

    @Test
    void 정치_3개_소스에서_기사를_가져온다() {
        List<RssService.NewsItem> items = service.fetchByCategory("정치", 6);

        assertThat(items).isNotEmpty();
        System.out.println("=== 정치 (" + items.size() + "건) ===");
        items.forEach(item ->
                System.out.println("[" + item.source() + "] " + item.title()));

        // 2개 이상 소스에서 가져왔는지 확인
        long sourceCount = items.stream().map(RssService.NewsItem::source).distinct().count();
        assertThat(sourceCount).as("최소 2개 소스에서 수집").isGreaterThanOrEqualTo(2);
    }

    @Test
    void 경제_3개_소스에서_기사를_가져온다() {
        List<RssService.NewsItem> items = service.fetchByCategory("경제", 6);

        assertThat(items).isNotEmpty();
        System.out.println("=== 경제 (" + items.size() + "건) ===");
        items.forEach(item ->
                System.out.println("[" + item.source() + "] " + item.title()));

        long sourceCount = items.stream().map(RssService.NewsItem::source).distinct().count();
        assertThat(sourceCount).as("최소 2개 소스에서 수집").isGreaterThanOrEqualTo(2);
    }

    @Test
    void IT_3개_소스에서_기사를_가져온다() {
        List<RssService.NewsItem> items = service.fetchByCategory("IT", 6);

        assertThat(items).isNotEmpty();

        List<String> sources = items.stream().map(RssService.NewsItem::source).distinct().toList();
        String detail = items.stream()
                .map(i -> "[" + i.source() + "] " + i.title())
                .reduce("", (a, b) -> a + "\n" + b);

        assertThat(sources.size())
                .as("IT는 2개 소스(동아/한경). 실제 소스: %s, 기사:%s", sources, detail)
                .isGreaterThanOrEqualTo(2);
    }
}
