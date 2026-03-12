package com.dailymail.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClaudeServiceTest {

    private MockWebServer mockServer;
    private ClaudeService claudeService;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();

        WebClient webClient = WebClient.builder()
                .baseUrl(mockServer.url("/").toString())
                .defaultHeader("x-api-key", "test-key")
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("content-type", "application/json")
                .build();
        claudeService = new ClaudeService(webClient, "claude-haiku-4-5-20251001");
    }

    @AfterEach
    void tearDown() throws IOException {
        mockServer.shutdown();
    }

    @Test
    void public_생성자로_생성() {
        ClaudeService service = new ClaudeService("fake-api-key", "test-model");
        assertThat(service).isNotNull();
    }

    @Test
    void ask_정상응답_파싱() throws Exception {
        Map<String, Object> response = Map.of(
                "content", List.of(Map.of("type", "text", "text", "테스트 응답입니다.")),
                "model", "claude-haiku-4-5-20251001",
                "role", "assistant"
        );
        mockServer.enqueue(new MockResponse()
                .setBody(new ObjectMapper().writeValueAsString(response))
                .addHeader("Content-Type", "application/json"));

        String result = claudeService.ask("테스트 프롬프트");

        assertThat(result).isEqualTo("테스트 응답입니다.");

        RecordedRequest request = mockServer.takeRequest();
        assertThat(request.getPath()).isEqualTo("/v1/messages");
        assertThat(request.getHeader("x-api-key")).isEqualTo("test-key");
    }

    @Test
    void ask_요청_바디에_모델과_프롬프트_포함() throws Exception {
        Map<String, Object> response = Map.of(
                "content", List.of(Map.of("type", "text", "text", "응답"))
        );
        mockServer.enqueue(new MockResponse()
                .setBody(new ObjectMapper().writeValueAsString(response))
                .addHeader("Content-Type", "application/json"));

        claudeService.ask("프로세스와 스레드의 차이는?");

        RecordedRequest request = mockServer.takeRequest();
        String body = request.getBody().readUtf8();
        assertThat(body).contains("claude-haiku-4-5-20251001");
        assertThat(body).contains("프로세스와 스레드의 차이는?");
        assertThat(body).contains("2048");
    }

    @Test
    void ask_응답에_content_없으면_예외() {
        mockServer.enqueue(new MockResponse()
                .setBody("{\"error\": \"invalid\"}")
                .addHeader("Content-Type", "application/json"));

        assertThatThrownBy(() -> claudeService.ask("프롬프트"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Claude API 응답 파싱 실패");
    }

    @Test
    void ask_빈_객체_응답이면_예외() {
        mockServer.enqueue(new MockResponse()
                .setBody("{}")
                .addHeader("Content-Type", "application/json"));

        assertThatThrownBy(() -> claudeService.ask("프롬프트"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void ask_null_응답이면_예외() {
        // 빈 body → bodyToMono가 빈 Map 반환 (content key 없음)
        mockServer.enqueue(new MockResponse()
                .addHeader("Content-Type", "application/json"));

        assertThatThrownBy(() -> claudeService.ask("프롬프트"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void ask_API_에러_응답시_WebClientResponseException() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(429)
                .setBody("{\"error\": \"rate_limit\"}")
                .addHeader("Content-Type", "application/json"));

        assertThatThrownBy(() -> claudeService.ask("프롬프트"))
                .isInstanceOf(WebClientResponseException.class);
    }

    @Test
    void ask_서버_에러_500_재시도_후에도_실패() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("{\"error\": \"internal\"}")
                .addHeader("Content-Type", "application/json"));
        mockServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("{\"error\": \"internal\"}")
                .addHeader("Content-Type", "application/json"));

        assertThatThrownBy(() -> claudeService.ask("프롬프트"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void ask_500_후_재시도_성공() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("{\"error\": \"internal\"}")
                .addHeader("Content-Type", "application/json"));

        Map<String, Object> successResponse = Map.of(
                "content", List.of(Map.of("type", "text", "text", "재시도 성공"))
        );
        mockServer.enqueue(new MockResponse()
                .setBody(new ObjectMapper().writeValueAsString(successResponse))
                .addHeader("Content-Type", "application/json"));

        String result = claudeService.ask("프롬프트");

        assertThat(result).isEqualTo("재시도 성공");
        assertThat(mockServer.getRequestCount()).isEqualTo(2);
    }

    @Test
    void ask_4xx_에러는_재시도하지_않음() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(400)
                .setBody("{\"error\": \"bad_request\"}")
                .addHeader("Content-Type", "application/json"));

        assertThatThrownBy(() -> claudeService.ask("프롬프트"))
                .isInstanceOf(WebClientResponseException.class);
        assertThat(mockServer.getRequestCount()).isEqualTo(1);
    }

    @Test
    void isTransient_5xx이면_true() {
        var exception = WebClientResponseException.create(500, "Internal Server Error", null, null, null);
        assertThat(ClaudeService.isTransient(exception)).isTrue();
    }

    @Test
    void isTransient_4xx이면_false() {
        var exception = WebClientResponseException.create(400, "Bad Request", null, null, null);
        assertThat(ClaudeService.isTransient(exception)).isFalse();
    }

    @Test
    void isTransient_기타_예외는_true() {
        assertThat(ClaudeService.isTransient(new RuntimeException("connection refused"))).isTrue();
    }
}
