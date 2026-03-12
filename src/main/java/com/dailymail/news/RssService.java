package com.dailymail.news;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class RssService {

    private static final Map<String, List<RssFeed>> FEEDS = Map.of(
            "정치", List.of(
                    new RssFeed("조선일보", "https://www.chosun.com/arc/outboundfeeds/rss/category/politics/?outputType=xml"),
                    new RssFeed("중앙일보", "https://rss.joins.com/joins_politics_list.xml"),
                    new RssFeed("동아일보", "https://rss.donga.com/politics.xml"),
                    new RssFeed("한국경제", "https://www.hankyung.com/feed/politics")
            ),
            "경제", List.of(
                    new RssFeed("조선일보", "https://www.chosun.com/arc/outboundfeeds/rss/category/economy/?outputType=xml"),
                    new RssFeed("중앙일보", "https://rss.joins.com/joins_money_list.xml"),
                    new RssFeed("동아일보", "https://rss.donga.com/economy.xml"),
                    new RssFeed("한국경제", "https://www.hankyung.com/feed/economy")
            ),
            "IT", List.of(
                    new RssFeed("조선일보", "https://www.chosun.com/arc/outboundfeeds/rss/category/economy/tech/?outputType=xml"),
                    new RssFeed("중앙일보", "https://rss.joins.com/joins_it_list.xml"),
                    new RssFeed("동아일보", "https://rss.donga.com/it.xml"),
                    new RssFeed("한국경제", "https://www.hankyung.com/feed/it")
            )
    );

    private final WebClient webClient;

    public RssService() {
        this.webClient = WebClient.builder()
                .codecs(config -> config.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }

    public List<NewsItem> fetchByCategory(String category, int limit) {
        return fetchFromCategory(category, limit);
    }

    private List<NewsItem> fetchFromCategory(String category, int limit) {
        List<RssFeed> feeds = FEEDS.get(category);
        if (feeds == null) return List.of();

        List<NewsItem> allItems = new ArrayList<>();
        for (RssFeed feed : feeds) {
            try {
                List<NewsItem> items = fetchFeed(feed);
                allItems.addAll(items);
            } catch (Exception e) {
                log.warn("[{}] RSS 수집 실패: {}", feed.name(), e.getMessage());
            }
        }

        return allItems.stream()
                .limit(limit)
                .toList();
    }

    private List<NewsItem> fetchFeed(RssFeed feed) {
        String xml = webClient.get()
                .uri(feed.url())
                .retrieve()
                .bodyToMono(String.class)
                .block();

        if (xml == null || xml.isBlank()) return List.of();

        return parseRss(xml, feed.name());
    }

    List<NewsItem> parseRss(String xml, String source) {
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
        if (nodes.getLength() > 0 && nodes.item(0).getTextContent() != null) {
            return nodes.item(0).getTextContent().trim();
        }
        return "";
    }

    public record RssFeed(String name, String url) {}

    public record NewsItem(String title, String link, String description, String source) {}
}
