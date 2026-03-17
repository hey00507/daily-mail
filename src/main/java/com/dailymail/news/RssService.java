package com.dailymail.news;

import lombok.extern.slf4j.Slf4j;
import com.dailymail.core.WebClientRetry;
import org.springframework.web.reactive.function.client.WebClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import reactor.util.retry.Retry;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
public class RssService {

    public static final Map<String, List<RssFeed>> DEFAULT_FEEDS = Map.of(
            "정치", List.of(
                    new RssFeed("조선일보", "https://www.chosun.com/arc/outboundfeeds/rss/category/politics/?outputType=xml"),
                    new RssFeed("동아일보", "https://rss.donga.com/politics.xml"),
                    new RssFeed("한국경제", "https://www.hankyung.com/feed/politics")
            ),
            "경제", List.of(
                    new RssFeed("조선일보", "https://www.chosun.com/arc/outboundfeeds/rss/category/economy/?outputType=xml"),
                    new RssFeed("동아일보", "https://rss.donga.com/economy.xml"),
                    new RssFeed("한국경제", "https://www.hankyung.com/feed/economy")
            ),
            "IT", List.of(
                    new RssFeed("조선일보", "https://www.chosun.com/arc/outboundfeeds/rss/category/economy/tech/?outputType=xml"),
                    new RssFeed("동아일보", "https://rss.donga.com/it.xml"),
                    new RssFeed("한국경제", "https://www.hankyung.com/feed/it")
            )
    );

    private final WebClient webClient;
    private final Map<String, List<RssFeed>> feeds;

    public RssService(WebClient webClient, Map<String, List<RssFeed>> feeds) {
        this.webClient = webClient;
        this.feeds = feeds;
    }

    public List<NewsItem> fetchByCategory(String category, int limit) {
        List<RssFeed> categoryFeeds = feeds.get(category);
        if (categoryFeeds == null) return List.of();

        int perSource = Math.max(2, (limit + categoryFeeds.size() - 1) / categoryFeeds.size());

        // 소스별로 균등 수집
        List<List<NewsItem>> perSourceItems = new ArrayList<>();
        for (RssFeed feed : categoryFeeds) {
            try {
                List<NewsItem> items = fetchFeed(feed);
                perSourceItems.add(items.stream().limit(perSource).toList());
                log.info("[{}] RSS 수집 성공: {}건", feed.name(), items.size());
            } catch (Exception e) {
                log.warn("[{}] RSS 수집 실패: {}", feed.name(), e.getMessage());
            }
        }

        // 라운드로빈 인터리빙 — 소스별 균등 배치 (결정적 순서)
        List<NewsItem> interleaved = new ArrayList<>();
        int maxSize = perSourceItems.stream().mapToInt(List::size).max().orElse(0);
        for (int i = 0; i < maxSize; i++) {
            for (List<NewsItem> items : perSourceItems) {
                if (i < items.size()) {
                    interleaved.add(items.get(i));
                }
            }
        }

        return interleaved.stream()
                .limit(limit)
                .toList();
    }

    private List<NewsItem> fetchFeed(RssFeed feed) {
        String xml = webClient.get()
                .uri(feed.url())
                .header("User-Agent", "Mozilla/5.0 (compatible; DailyMail/1.0; +https://github.com/hey00507/daily-mail)")
                .retrieve()
                .bodyToMono(String.class)
                .retryWhen(Retry.backoff(1, Duration.ofSeconds(1))
                        .filter(WebClientRetry::isTransient))
                .block(Duration.ofSeconds(10));

        if (xml == null || xml.isBlank()) return List.of();

        return parseRss(xml, feed.name());
    }

    private List<NewsItem> parseRss(String xml, String source) {
        List<NewsItem> items = new ArrayList<>();
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            var builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xml)));

            // RSS 2.0 (<item>) or Atom (<entry>) 둘 다 지원
            NodeList entries = doc.getElementsByTagName("item");
            if (entries.getLength() == 0) {
                entries = doc.getElementsByTagName("entry");
            }

            for (int i = 0; i < Math.min(entries.getLength(), 5); i++) {
                Element entry = (Element) entries.item(i);
                String title = getTagText(entry, "title");
                String link = getTagText(entry, "link");
                String description = getTagText(entry, "description");

                if (link.isEmpty()) {
                    // Atom format: <link href="..."/>
                    NodeList links = entry.getElementsByTagName("link");
                    if (links.getLength() > 0) {
                        link = ((Element) links.item(0)).getAttribute("href");
                    }
                }

                if (!title.isEmpty()) {
                    items.add(new NewsItem(title, link, description, source));
                }
            }
        } catch (Exception e) {
            log.warn("[{}] RSS 파싱 실패: {}", source, e.getMessage());
        }
        return items;
    }

    private String getTagText(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent().trim();
        }
        return "";
    }

    public record RssFeed(String name, String url) {}

    public record NewsItem(String title, String link, String description, String source) {}
}
