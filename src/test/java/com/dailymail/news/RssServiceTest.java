package com.dailymail.news;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RssServiceTest {

    private final RssService service = new RssService();

    // --- parseRss 테스트 ---

    @Test
    void parseRss_정상_RSS2_파싱() {
        String rssXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <item>
                      <title>테스트 기사 1</title>
                      <link>https://example.com/1</link>
                      <description>기사 내용 1</description>
                    </item>
                    <item>
                      <title>테스트 기사 2</title>
                      <link>https://example.com/2</link>
                      <description>기사 내용 2</description>
                    </item>
                  </channel>
                </rss>
                """;

        List<RssService.NewsItem> items = service.parseRss(rssXml, "TestSource");

        assertThat(items).hasSize(2);
        assertThat(items.get(0).title()).isEqualTo("테스트 기사 1");
        assertThat(items.get(0).link()).isEqualTo("https://example.com/1");
        assertThat(items.get(0).description()).isEqualTo("기사 내용 1");
        assertThat(items.get(0).source()).isEqualTo("TestSource");
    }

    @Test
    void parseRss_Atom_포맷_파싱() {
        String atomXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <entry>
                    <title>Atom 기사</title>
                    <link href="https://example.com/atom"/>
                    <description>Atom 내용</description>
                  </entry>
                </feed>
                """;

        List<RssService.NewsItem> items = service.parseRss(atomXml, "AtomSource");

        assertThat(items).hasSize(1);
        assertThat(items.get(0).title()).isEqualTo("Atom 기사");
        assertThat(items.get(0).link()).isEqualTo("https://example.com/atom");
    }

    @Test
    void parseRss_최대_5개만_파싱() {
        StringBuilder xml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0"><channel>
                """);
        for (int i = 0; i < 10; i++) {
            xml.append("<item><title>기사 ").append(i).append("</title><link>https://example.com/</link><description></description></item>");
        }
        xml.append("</channel></rss>");

        List<RssService.NewsItem> items = service.parseRss(xml.toString(), "Test");

        assertThat(items).hasSize(5);
    }

    @Test
    void parseRss_잘못된_XML이면_빈_리스트() {
        List<RssService.NewsItem> items = service.parseRss("not xml", "Test");

        assertThat(items).isEmpty();
    }

    @Test
    void parseRss_빈_XML이면_빈_리스트() {
        List<RssService.NewsItem> items = service.parseRss("", "Test");

        assertThat(items).isEmpty();
    }

    @Test
    void parseRss_제목_없는_항목은_스킵() {
        String rssXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <item>
                      <title></title>
                      <link>https://example.com/1</link>
                      <description>내용</description>
                    </item>
                    <item>
                      <title>유효한 기사</title>
                      <link>https://example.com/2</link>
                      <description>내용</description>
                    </item>
                  </channel>
                </rss>
                """;

        List<RssService.NewsItem> items = service.parseRss(rssXml, "Test");

        assertThat(items).hasSize(1);
        assertThat(items.get(0).title()).isEqualTo("유효한 기사");
    }

    @Test
    void parseRss_description_없는_항목() {
        String rssXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <item>
                      <title>제목만 있는 기사</title>
                      <link>https://example.com/1</link>
                    </item>
                  </channel>
                </rss>
                """;

        List<RssService.NewsItem> items = service.parseRss(rssXml, "Test");

        assertThat(items).hasSize(1);
        assertThat(items.get(0).title()).isEqualTo("제목만 있는 기사");
        assertThat(items.get(0).description()).isEmpty();
    }

    @Test
    void parseRss_Atom_링크_없는_entry() {
        String atomXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <entry>
                    <title>링크 없는 기사</title>
                    <description>내용</description>
                  </entry>
                </feed>
                """;

        List<RssService.NewsItem> items = service.parseRss(atomXml, "Test");

        assertThat(items).hasSize(1);
        assertThat(items.get(0).title()).isEqualTo("링크 없는 기사");
        assertThat(items.get(0).link()).isEmpty();
    }

    @Test
    void parseRss_item도_entry도_없는_XML이면_빈_리스트() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <title>빈 채널</title>
                  </channel>
                </rss>
                """;

        List<RssService.NewsItem> items = service.parseRss(xml, "Test");
        assertThat(items).isEmpty();
    }

    @Test
    void NewsItem_record_생성() {
        var item = new RssService.NewsItem("제목", "https://link.com", "설명", "출처");
        assertThat(item.title()).isEqualTo("제목");
        assertThat(item.link()).isEqualTo("https://link.com");
        assertThat(item.description()).isEqualTo("설명");
        assertThat(item.source()).isEqualTo("출처");
    }

    @Test
    void fetchByCategory_존재하지_않는_카테고리면_빈_리스트() {
        List<RssService.NewsItem> items = service.fetchByCategory("존재하지않는카테고리", 5);
        assertThat(items).isEmpty();
    }

    // --- fetchByCategory + fetchFeed (MockWebServer) ---

    private MockWebServer mockServer;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (mockServer != null) {
            mockServer.shutdown();
        }
    }

    private String rssResponse(String title) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <item>
                      <title>%s</title>
                      <link>https://example.com/1</link>
                      <description>내용</description>
                    </item>
                  </channel>
                </rss>
                """.formatted(title);
    }

    @Test
    void fetchByCategory_MockWebServer로_정상_수집() {
        mockServer.enqueue(new MockResponse().setBody(rssResponse("기사A")).addHeader("Content-Type", "application/xml"));
        mockServer.enqueue(new MockResponse().setBody(rssResponse("기사B")).addHeader("Content-Type", "application/xml"));

        String baseUrl = mockServer.url("/").toString();
        var testFeeds = Map.of("테스트", List.of(
                new RssService.RssFeed("소스1", baseUrl + "feed1"),
                new RssService.RssFeed("소스2", baseUrl + "feed2")
        ));

        WebClient webClient = WebClient.builder()
                .codecs(config -> config.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
        RssService testService = new RssService(webClient, testFeeds);

        List<RssService.NewsItem> items = testService.fetchByCategory("테스트", 10);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).title()).isEqualTo("기사A");
        assertThat(items.get(1).title()).isEqualTo("기사B");
    }

    @Test
    void fetchByCategory_limit_적용() {
        mockServer.enqueue(new MockResponse().setBody(rssResponse("기사A")).addHeader("Content-Type", "application/xml"));
        mockServer.enqueue(new MockResponse().setBody(rssResponse("기사B")).addHeader("Content-Type", "application/xml"));
        mockServer.enqueue(new MockResponse().setBody(rssResponse("기사C")).addHeader("Content-Type", "application/xml"));

        String baseUrl = mockServer.url("/").toString();
        var testFeeds = Map.of("테스트", List.of(
                new RssService.RssFeed("소스1", baseUrl + "feed1"),
                new RssService.RssFeed("소스2", baseUrl + "feed2"),
                new RssService.RssFeed("소스3", baseUrl + "feed3")
        ));

        WebClient webClient = WebClient.builder()
                .codecs(config -> config.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
        RssService testService = new RssService(webClient, testFeeds);

        List<RssService.NewsItem> items = testService.fetchByCategory("테스트", 2);

        assertThat(items).hasSize(2);
    }

    @Test
    void fetchByCategory_일부_피드_실패해도_나머지_수집() {
        mockServer.enqueue(new MockResponse().setResponseCode(500).setBody("Internal Server Error"));
        mockServer.enqueue(new MockResponse().setBody(rssResponse("성공 기사")).addHeader("Content-Type", "application/xml"));

        String baseUrl = mockServer.url("/").toString();
        var testFeeds = Map.of("테스트", List.of(
                new RssService.RssFeed("실패소스", baseUrl + "fail"),
                new RssService.RssFeed("성공소스", baseUrl + "ok")
        ));

        WebClient webClient = WebClient.builder()
                .codecs(config -> config.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
        RssService testService = new RssService(webClient, testFeeds);

        List<RssService.NewsItem> items = testService.fetchByCategory("테스트", 10);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).title()).isEqualTo("성공 기사");
    }

    @Test
    void fetchByCategory_null_응답_피드() {
        // 서버가 빈 body를 주면 WebClient가 null 반환할 수 있음
        // null 대신 blank로 테스트 (WebClient는 보통 blank 반환)
        mockServer.enqueue(new MockResponse()
                .setBody("   ")
                .addHeader("Content-Type", "application/xml"));

        String baseUrl = mockServer.url("/").toString();
        var testFeeds = Map.of("테스트", List.of(
                new RssService.RssFeed("소스", baseUrl + "null-feed")
        ));

        WebClient webClient = WebClient.builder()
                .codecs(config -> config.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
        RssService testService = new RssService(webClient, testFeeds);

        List<RssService.NewsItem> items = testService.fetchByCategory("테스트", 10);
        assertThat(items).isEmpty();
    }

    @Test
    void fetchByCategory_빈_응답_피드() {
        mockServer.enqueue(new MockResponse().setBody("").addHeader("Content-Type", "application/xml"));

        String baseUrl = mockServer.url("/").toString();
        var testFeeds = Map.of("테스트", List.of(
                new RssService.RssFeed("빈소스", baseUrl + "empty")
        ));

        WebClient webClient = WebClient.builder()
                .codecs(config -> config.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
        RssService testService = new RssService(webClient, testFeeds);

        List<RssService.NewsItem> items = testService.fetchByCategory("테스트", 10);

        assertThat(items).isEmpty();
    }
}
