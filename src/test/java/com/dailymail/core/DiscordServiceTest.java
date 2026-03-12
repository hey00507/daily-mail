package com.dailymail.core;

import com.dailymail.config.DiscordConfig;
import com.dailymail.news.NewsBriefMail;
import com.dailymail.today.CalendarService;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiscordServiceTest {

    // --- isEnabled ---

    @Test
    void 토큰이_비어있으면_비활성화() {
        var config = new DiscordConfig("", "", Map.of());
        var service = new DiscordService(config);
        assertThat(service.isEnabled()).isFalse();
    }

    @Test
    void 토큰과_기본채널ID_있으면_활성화() {
        var config = new DiscordConfig("test-token", "123456", Map.of());
        var service = new DiscordService(config);
        assertThat(service.isEnabled()).isTrue();
    }

    @Test
    void 기본채널ID_없어도_모듈별_채널_있으면_활성화() {
        var config = new DiscordConfig("test-token", "", Map.of("cs-daily", "111"));
        var service = new DiscordService(config);
        assertThat(service.isEnabled()).isTrue();
    }

    @Test
    void 토큰_없으면_모듈별_채널_있어도_비활성화() {
        var config = new DiscordConfig("", "", Map.of("cs-daily", "111"));
        var service = new DiscordService(config);
        assertThat(service.isEnabled()).isFalse();
    }

    // --- send 스킵 조건 ---

    @Test
    void 비활성화_상태면_send_스킵() {
        var config = new DiscordConfig("", "", Map.of());
        var service = new DiscordService(config);
        var content = new MailContent("[CS] 테스트", "cs-daily", Map.of("contentMarkdown", "테스트"));
        service.send(content);
    }

    @Test
    void 지원하지_않는_템플릿이면_send_스킵() {
        var config = new DiscordConfig("test-token", "123", Map.of());
        var service = new DiscordService(config);
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
        var webClient = WebClient.builder()
                .baseUrl(mockServer.url("/").toString())
                .build();
        return new DiscordService(config, webClient);
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
        assertThat(body).contains("AI 뉴스");
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
        // MockWebServer를 종료하여 연결 실패 유발
        mockServer.shutdown();

        var config = new DiscordConfig("test-bot-token", "999", Map.of());
        var webClient = WebClient.builder()
                .baseUrl("http://localhost:" + mockServer.getPort())
                .build();
        var service = new DiscordService(config, webClient);

        var content = new MailContent("[CS] 테스트", "cs-daily", Map.of("contentMarkdown", "내용"));

        assertThatThrownBy(() -> service.send(content))
                .isInstanceOf(DiscordSendException.class)
                .hasMessageContaining("Discord 발송 중 오류");

        // tearDown에서 다시 shutdown 호출되지 않도록
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

    // --- toDiscordMessage 포맷팅 ---

    @Test
    void toDiscordMessage_cs_daily_마크다운_포맷() {
        var config = new DiscordConfig("token", "123", Map.of());
        var service = new DiscordService(config);

        var content = new MailContent("[CS] OS: 프로세스", "cs-daily", Map.of(
                "contentMarkdown", "## 면접 질문\n프로세스와 스레드의 차이는?"
        ));

        String message = service.toDiscordMessage(content);

        assertThat(message).contains("## [CS] OS: 프로세스");
        assertThat(message).contains("## 면접 질문");
        assertThat(message).contains("프로세스와 스레드의 차이는?");
    }

    @Test
    void toDiscordMessage_cs_daily_contentMarkdown_없으면_빈_본문() {
        var config = new DiscordConfig("token", "123", Map.of());
        var service = new DiscordService(config);

        var content = new MailContent("[CS] 테스트", "cs-daily", Map.of());

        String message = service.toDiscordMessage(content);

        assertThat(message).contains("## [CS] 테스트");
        assertThat(message.trim()).isEqualTo("## [CS] 테스트");
    }

    @Test
    void toDiscordMessage_news_brief_포맷() {
        var config = new DiscordConfig("token", "123", Map.of());
        var service = new DiscordService(config);

        var newsMap = new LinkedHashMap<String, List<NewsBriefMail.SummarizedNews>>();
        newsMap.put("IT", List.of(
                new NewsBriefMail.SummarizedNews("AI 뉴스", "AI 요약", "https://example.com", "조선일보", "IT")
        ));

        var content = new MailContent("[News] 03/12", "news-brief", Map.of("newsMap", newsMap));

        String message = service.toDiscordMessage(content);

        assertThat(message).contains("### IT");
        assertThat(message).contains("**AI 뉴스**");
        assertThat(message).contains("AI 요약");
        assertThat(message).contains("조선일보");
        assertThat(message).contains("https://example.com");
    }

    @Test
    void toDiscordMessage_news_brief_요약_없는_항목() {
        var config = new DiscordConfig("token", "123", Map.of());
        var service = new DiscordService(config);

        var newsMap = new LinkedHashMap<String, List<NewsBriefMail.SummarizedNews>>();
        newsMap.put("정치", List.of(
                new NewsBriefMail.SummarizedNews("정치 뉴스", "", "https://example.com", "중앙일보", "정치")
        ));

        var content = new MailContent("[News] 테스트", "news-brief", Map.of("newsMap", newsMap));

        String message = service.toDiscordMessage(content);

        assertThat(message).contains("**정치 뉴스**");
        assertThat(message).doesNotContain(" — ");
    }

    @Test
    void toDiscordMessage_news_brief_링크_없는_항목() {
        var config = new DiscordConfig("token", "123", Map.of());
        var service = new DiscordService(config);

        var newsMap = new LinkedHashMap<String, List<NewsBriefMail.SummarizedNews>>();
        newsMap.put("경제", List.of(
                new NewsBriefMail.SummarizedNews("경제 뉴스", "요약", "", "동아일보", "경제")
        ));

        var content = new MailContent("[News] 테스트", "news-brief", Map.of("newsMap", newsMap));

        String message = service.toDiscordMessage(content);

        assertThat(message).contains("**경제 뉴스**");
        assertThat(message).doesNotContain("   http");
    }

    @Test
    void toDiscordMessage_news_brief_newsMap_null이면_헤더만() {
        var config = new DiscordConfig("token", "123", Map.of());
        var service = new DiscordService(config);

        var content = new MailContent("[News] 테스트", "news-brief", Map.of());

        String message = service.toDiscordMessage(content);

        assertThat(message).contains("## [News] 테스트");
    }

    @Test
    void toDiscordMessage_today_brief_일정_있는_경우() {
        var config = new DiscordConfig("token", "123", Map.of());
        var service = new DiscordService(config);

        var events = List.of(
                new CalendarService.CalendarEvent("팀 미팅", "10:00", false, "회의실 A", null),
                new CalendarService.CalendarEvent("점심 약속", "12:00", false, null, null)
        );

        var content = new MailContent("[Today] 03/12", "today-brief", Map.of(
                "hasEvents", true,
                "events", events
        ));

        String message = service.toDiscordMessage(content);

        assertThat(message).contains("- **10:00** 팀 미팅 (회의실 A)");
        assertThat(message).contains("- **12:00** 점심 약속");
        assertThat(message).doesNotContain("점심 약속 (");
    }

    @Test
    void toDiscordMessage_today_brief_일정_없으면_안내_메시지() {
        var config = new DiscordConfig("token", "123", Map.of());
        var service = new DiscordService(config);

        var content = new MailContent("[Today] 03/12", "today-brief", Map.of(
                "hasEvents", false,
                "events", List.of()
        ));

        String message = service.toDiscordMessage(content);

        assertThat(message).contains("오늘 일정이 없습니다.");
    }

    @Test
    void toDiscordMessage_알수없는_템플릿() {
        var config = new DiscordConfig("token", "123", Map.of());
        var service = new DiscordService(config);

        var content = new MailContent("제목", "unknown-template", Map.of());

        String message = service.toDiscordMessage(content);

        assertThat(message).contains("알 수 없는 템플릿: unknown-template");
    }

    // --- isEnabled 브랜치 보강 ---

    @Test
    void 토큰_있고_기본채널_blank_모듈채널_비어있으면_비활성화() {
        var config = new DiscordConfig("test-token", "", Map.of());
        var service = new DiscordService(config);
        assertThat(service.isEnabled()).isFalse();
    }

    // --- resolveChannelId 브랜치 보강 ---

    @Test
    void send_모듈별_채널_blank이면_기본_채널_사용() throws Exception {
        mockServer.enqueue(new MockResponse().setBody("{}").addHeader("Content-Type", "application/json"));

        var service = createServiceWithMockServer("default-ch", Map.of("cs-daily", ""));
        var content = new MailContent("[CS] 테스트", "cs-daily", Map.of("contentMarkdown", "내용"));

        service.send(content);

        RecordedRequest request = mockServer.takeRequest();
        assertThat(request.getPath()).isEqualTo("/channels/default-ch/messages");
    }

    // --- formatTodayBrief 브랜치 보강 ---

    @Test
    void toDiscordMessage_today_brief_hasEvents_없으면_안내_메시지() {
        var config = new DiscordConfig("token", "123", Map.of());
        var service = new DiscordService(config);

        var content = new MailContent("[Today] 03/12", "today-brief", Map.of());

        String message = service.toDiscordMessage(content);
        assertThat(message).contains("오늘 일정이 없습니다.");
    }

    @Test
    void toDiscordMessage_today_brief_events_null이면_안내_메시지() {
        var config = new DiscordConfig("token", "123", Map.of());
        var service = new DiscordService(config);

        var content = new MailContent("[Today] 03/12", "today-brief", Map.of(
                "hasEvents", true
        ));

        String message = service.toDiscordMessage(content);
        assertThat(message).contains("오늘 일정이 없습니다.");
    }

    @Test
    void toDiscordMessage_today_brief_hasEvents_true이지만_events_빈리스트() {
        var config = new DiscordConfig("token", "123", Map.of());
        var service = new DiscordService(config);

        var content = new MailContent("[Today] 03/12", "today-brief", Map.of(
                "hasEvents", true,
                "events", List.of()
        ));

        String message = service.toDiscordMessage(content);
        assertThat(message).contains("오늘 일정이 없습니다.");
    }

    @Test
    void toDiscordMessage_today_brief_위치_빈문자열() {
        var config = new DiscordConfig("token", "123", Map.of());
        var service = new DiscordService(config);

        var events = List.of(
                new CalendarService.CalendarEvent("미팅", "10:00", false, "", null)
        );

        var content = new MailContent("[Today] 03/12", "today-brief", Map.of(
                "hasEvents", true,
                "events", events
        ));

        String message = service.toDiscordMessage(content);
        assertThat(message).contains("- **10:00** 미팅");
        assertThat(message).doesNotContain("미팅 (");
    }

    // --- splitMessage 브랜치 보강 ---

    @Test
    void splitMessage_줄바꿈_없이_2000자_초과() {
        var config = new DiscordConfig("token", "123", Map.of());
        var service = new DiscordService(config);

        String noNewlines = "x".repeat(3000);
        List<String> chunks = service.splitMessage(noNewlines);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0)).hasSize(2000);
        assertThat(chunks.get(1)).hasSize(1000);
        assertThat(String.join("", chunks)).isEqualTo(noNewlines);
    }

    // --- splitMessage ---

    @Test
    void splitMessage_2000자_이하면_분할_안함() {
        var config = new DiscordConfig("token", "123", Map.of());
        var service = new DiscordService(config);

        String short_message = "짧은 메시지";
        List<String> chunks = service.splitMessage(short_message);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).isEqualTo(short_message);
    }

    @Test
    void splitMessage_2000자_초과시_줄바꿈_기준_분할() {
        var config = new DiscordConfig("token", "123", Map.of());
        var service = new DiscordService(config);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            sb.append("Line ").append(String.format("%02d", i))
              .append(": ").append("x".repeat(40)).append("\n");
        }
        String longMessage = sb.toString();
        assertThat(longMessage.length()).isGreaterThan(2000);

        List<String> chunks = service.splitMessage(longMessage);

        assertThat(chunks.size()).isGreaterThan(1);
        for (String chunk : chunks) {
            assertThat(chunk.length()).isLessThanOrEqualTo(2000);
        }
        assertThat(String.join("", chunks)).isEqualTo(longMessage);
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
