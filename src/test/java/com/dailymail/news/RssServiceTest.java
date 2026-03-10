package com.dailymail.news;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RssServiceTest {

    private MockWebServer mockServer;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockServer.shutdown();
    }

    @Test
    void parseRss_정상_RSS2_파싱() throws Exception {
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

        RssService service = new RssService();
        var method = RssService.class.getDeclaredMethod("parseRss", String.class, String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<RssService.NewsItem> items = (List<RssService.NewsItem>) method.invoke(service, rssXml, "TestSource");

        assertThat(items).hasSize(2);
        assertThat(items.get(0).title()).isEqualTo("테스트 기사 1");
        assertThat(items.get(0).link()).isEqualTo("https://example.com/1");
        assertThat(items.get(0).source()).isEqualTo("TestSource");
    }

    @Test
    void parseRss_Atom_포맷_파싱() throws Exception {
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

        RssService service = new RssService();
        var method = RssService.class.getDeclaredMethod("parseRss", String.class, String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<RssService.NewsItem> items = (List<RssService.NewsItem>) method.invoke(service, atomXml, "AtomSource");

        assertThat(items).hasSize(1);
        assertThat(items.get(0).title()).isEqualTo("Atom 기사");
    }

    @Test
    void parseRss_최대_5개만_파싱() throws Exception {
        StringBuilder xml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0"><channel>
                """);
        for (int i = 0; i < 10; i++) {
            xml.append("<item><title>기사 ").append(i).append("</title><link>https://example.com/</link><description></description></item>");
        }
        xml.append("</channel></rss>");

        RssService service = new RssService();
        var method = RssService.class.getDeclaredMethod("parseRss", String.class, String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<RssService.NewsItem> items = (List<RssService.NewsItem>) method.invoke(service, xml.toString(), "Test");

        assertThat(items).hasSize(5);
    }

    @Test
    void parseRss_잘못된_XML이면_빈_리스트() throws Exception {
        RssService service = new RssService();
        var method = RssService.class.getDeclaredMethod("parseRss", String.class, String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<RssService.NewsItem> items = (List<RssService.NewsItem>) method.invoke(service, "not xml", "Test");

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
}
