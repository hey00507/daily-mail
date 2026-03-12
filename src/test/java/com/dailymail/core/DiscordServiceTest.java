package com.dailymail.core;

import com.dailymail.config.DiscordConfig;
import com.dailymail.news.NewsBriefMail;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiscordServiceTest {

    private static final String UNUSED_URL = "https://discord.com/api/v10";

    // --- isEnabled ---

    @Test
    void 토큰이_비어있으면_비활성화() {
        var config = new DiscordConfig("", "", Map.of());
        var service = new DiscordService(config, UNUSED_URL);
        assertThat(service.isEnabled()).isFalse();
    }

    @Test
    void 토큰과_기본채널ID_있으면_활성화() {
        var config = new DiscordConfig("test-token", "123456", Map.of());
        var service = new DiscordService(config, UNUSED_URL);
        assertThat(service.isEnabled()).isTrue();
    }

    @Test
    void 기본채널ID_없어도_모듈별_채널_있으면_활성화() {
        var config = new DiscordConfig("test-token", "", Map.of("cs-daily", "111"));
        var service = new DiscordService(config, UNUSED_URL);
        assertThat(service.isEnabled()).isTrue();
    }

    @Test
    void 토큰_없으면_모듈별_채널_있어도_비활성화() {
        var config = new DiscordConfig("", "", Map.of("cs-daily", "111"));
        var service = new DiscordService(config, UNUSED_URL);
        assertThat(service.isEnabled()).isFalse();
    }

    @Test
    void 토큰_있고_기본채널_blank_모듈채널_비어있으면_비활성화() {
        var config = new DiscordConfig("test-token", "", Map.of());
        var service = new DiscordService(config, UNUSED_URL);
        assertThat(service.isEnabled()).isFalse();
    }

    // --- send 스킵 조건 ---

    @Test
    void 비활성화_상태면_send_스킵() {
        var config = new DiscordConfig("", "", Map.of());
        var service = new DiscordService(config, UNUSED_URL);
        var content = new MailContent("[CS] 테스트", "cs-daily", Map.of("contentMarkdown", "테스트"));
        service.send(content);
    }

    @Test
    void 지원하지_않는_템플릿이면_send_스킵() {
        var config = new DiscordConfig("test-token", "123", Map.of());
        var service = new DiscordService(config, UNUSED_URL);
        var content = new MailContent("[Today] 테스트", "today-brief", Map.of());
        service.send(content);
    }

    // --- send 실제 HTTP 호출 (MockWebServer) ---

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

    private DiscordService createServiceWithMockServer(String channelId, Map<String, String> channels) {
        var config = new DiscordConfig("test-bot-token", channelId, channels);
        return new DiscordService(config, mockServer.url("/").toString());
    }

    @Test
    void send_cs_daily_정상_발송() throws Exception {
        mockServer.enqueue(new MockResponse().setBody("{}").addHeader("Content-Type", "application/json"));

        var service = createServiceWithMockServer("999", Map.of());
        var content = new MailContent("[CS] OS: 프로세스", "cs-daily", Map.of(
                "contentMarkdown", "## 질문\n프로세스란?"
        ));

        service.send(content);

        RecordedRequest request = mockServer.takeRequest();
        assertThat(request.getPath()).isEqualTo("/channels/999/messages");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bot test-bot-token");
        String body = request.getBody().readUtf8();
        assertThat(body).contains("프로세스란?");
    }

    @Test
    void send_cs_daily_마크다운_포맷_포함() throws Exception {
        mockServer.enqueue(new MockResponse().setBody("{}").addHeader("Content-Type", "application/json"));

        var service = createServiceWithMockServer("999", Map.of());
        var content = new MailContent("[CS] OS: 프로세스", "cs-daily", Map.of(
                "contentMarkdown", "## 면접 질문\n프로세스와 스레드의 차이는?"
        ));

        service.send(content);

        RecordedRequest request = mockServer.takeRequest();
        String body = request.getBody().readUtf8();
        assertThat(body).contains("## [CS] OS: 프로세스");
        assertThat(body).contains("## 면접 질문");
        assertThat(body).contains("프로세스와 스레드의 차이는?");
    }

    @Test
    void send_cs_daily_contentMarkdown_없으면_제목만() throws Exception {
        mockServer.enqueue(new MockResponse().setBody("{}").addHeader("Content-Type", "application/json"));

        var service = createServiceWithMockServer("999", Map.of());
        var content = new MailContent("[CS] 테스트", "cs-daily", Map.of());

        service.send(content);

        RecordedRequest request = mockServer.takeRequest();
        String body = request.getBody().readUtf8();
        assertThat(body).contains("## [CS] 테스트");
    }

    @Test
    void send_news_brief_정상_발송() throws Exception {
        mockServer.enqueue(new MockResponse().setBody("{}").addHeader("Content-Type", "application/json"));

        var service = createServiceWithMockServer("999", Map.of("news-brief", "777"));

        var newsMap = new LinkedHashMap<String, List<NewsBriefMail.SummarizedNews>>();
        newsMap.put("IT", List.of(
                new NewsBriefMail.SummarizedNews("AI 뉴스", "AI 요약", "https://example.com", "조선일보", "IT")
        ));

        var content = new MailContent("[News] 03/12", "news-brief", Map.of("newsMap", newsMap));

        service.send(content);

        RecordedRequest request = mockServer.takeRequest();
        assertThat(request.getPath()).isEqualTo("/channels/777/messages");
        String body = request.getBody().readUtf8();
        assertThat(body).contains("### IT");
        assertThat(body).contains("AI 뉴스");
        assertThat(body).contains("AI 요약");
        assertThat(body).contains("조선일보");
        assertThat(body).contains("https://example.com");
    }

    @Test
    void send_news_brief_요약_없는_항목() throws Exception {
        mockServer.enqueue(new MockResponse().setBody("{}").addHeader("Content-Type", "application/json"));

        var service = createServiceWithMockServer("999", Map.of());

        var newsMap = new LinkedHashMap<String, List<NewsBriefMail.SummarizedNews>>();
        newsMap.put("정치", List.of(
                new NewsBriefMail.SummarizedNews("정치 뉴스", "", "https://example.com", "중앙일보", "정치")
        ));

        var content = new MailContent("[News] 테스트", "news-brief", Map.of("newsMap", newsMap));

        service.send(content);

        RecordedRequest request = mockServer.takeRequest();
        String body = request.getBody().readUtf8();
        assertThat(body).contains("정치 뉴스");
        assertThat(body).doesNotContain(" — ");
    }

    @Test
    void send_news_brief_링크_없는_항목() throws Exception {
        mockServer.enqueue(new MockResponse().setBody("{}").addHeader("Content-Type", "application/json"));

        var service = createServiceWithMockServer("999", Map.of());

        var newsMap = new LinkedHashMap<String, List<NewsBriefMail.SummarizedNews>>();
        newsMap.put("경제", List.of(
                new NewsBriefMail.SummarizedNews("경제 뉴스", "요약", "", "동아일보", "경제")
        ));

        var content = new MailContent("[News] 테스트", "news-brief", Map.of("newsMap", newsMap));

        service.send(content);

        RecordedRequest request = mockServer.takeRequest();
        String body = request.getBody().readUtf8();
        assertThat(body).contains("경제 뉴스");
    }

    @Test
    void send_news_brief_newsMap_null이면_헤더만() throws Exception {
        mockServer.enqueue(new MockResponse().setBody("{}").addHeader("Content-Type", "application/json"));

        var service = createServiceWithMockServer("999", Map.of());
        var content = new MailContent("[News] 테스트", "news-brief", Map.of());

        service.send(content);

        RecordedRequest request = mockServer.takeRequest();
        String body = request.getBody().readUtf8();
        assertThat(body).contains("## [News] 테스트");
    }

    @Test
    void send_모듈별_채널_없으면_기본_채널_사용() throws Exception {
        mockServer.enqueue(new MockResponse().setBody("{}").addHeader("Content-Type", "application/json"));

        var service = createServiceWithMockServer("default-ch", Map.of());
        var content = new MailContent("[CS] 테스트", "cs-daily", Map.of("contentMarkdown", "내용"));

        service.send(content);

        RecordedRequest request = mockServer.takeRequest();
        assertThat(request.getPath()).isEqualTo("/channels/default-ch/messages");
    }

    @Test
    void send_모듈별_채널_blank이면_기본_채널_사용() throws Exception {
        mockServer.enqueue(new MockResponse().setBody("{}").addHeader("Content-Type", "application/json"));

        var service = createServiceWithMockServer("default-ch", Map.of("cs-daily", ""));
        var content = new MailContent("[CS] 테스트", "cs-daily", Map.of("contentMarkdown", "내용"));

        service.send(content);

        RecordedRequest request = mockServer.takeRequest();
        assertThat(request.getPath()).isEqualTo("/channels/default-ch/messages");
    }

    @Test
    void send_API_에러시_DiscordSendException() {
        mockServer.enqueue(new MockResponse().setResponseCode(403).setBody("{\"message\":\"Missing Access\"}"));

        var service = createServiceWithMockServer("999", Map.of());
        var content = new MailContent("[CS] 테스트", "cs-daily", Map.of("contentMarkdown", "내용"));

        assertThatThrownBy(() -> service.send(content))
                .isInstanceOf(DiscordSendException.class)
                .hasMessageContaining("Discord API 응답 오류")
                .hasMessageContaining("403");
    }

    @Test
    void send_연결_실패시_일반_DiscordSendException() throws IOException {
        mockServer.shutdown();

        var config = new DiscordConfig("test-bot-token", "999", Map.of());
        var service = new DiscordService(config, "http://localhost:" + mockServer.getPort());

        var content = new MailContent("[CS] 테스트", "cs-daily", Map.of("contentMarkdown", "내용"));

        assertThatThrownBy(() -> service.send(content))
                .isInstanceOf(DiscordSendException.class)
                .hasMessageContaining("Discord 발송 중 오류");

        mockServer = null;
    }

    @Test
    void send_긴_메시지_분할_발송() throws Exception {
        StringBuilder longContent = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            longContent.append("Line ").append(i).append(": ").append("x".repeat(40)).append("\n");
        }

        mockServer.enqueue(new MockResponse().setBody("{}").addHeader("Content-Type", "application/json"));
        mockServer.enqueue(new MockResponse().setBody("{}").addHeader("Content-Type", "application/json"));

        var service = createServiceWithMockServer("999", Map.of());
        var content = new MailContent("[CS] 테스트", "cs-daily", Map.of(
                "contentMarkdown", longContent.toString()
        ));

        service.send(content);

        assertThat(mockServer.getRequestCount()).isGreaterThanOrEqualTo(2);
    }

    // --- DiscordSendException ---

    @Test
    void DiscordSendException_상태코드_포함() {
        var ex = new DiscordSendException("404 에러", 404, new RuntimeException("cause"));
        assertThat(ex.getStatusCode()).isEqualTo(404);
        assertThat(ex.getMessage()).isEqualTo("404 에러");
        assertThat(ex.getCause()).isInstanceOf(RuntimeException.class);
    }

    @Test
    void DiscordSendException_상태코드_없는_생성자() {
        var ex = new DiscordSendException("일반 에러", new RuntimeException("cause"));
        assertThat(ex.getStatusCode()).isEqualTo(0);
        assertThat(ex.getMessage()).isEqualTo("일반 에러");
    }
}
