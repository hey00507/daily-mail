package com.dailymail.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

        // ClaudeService의 baseUrl을 MockWebServer로 교체하기 위해 리플렉션 대신
        // 테스트용 생성자 패턴 — 여기서는 WebClient를 직접 주입할 수 없으므로
        // MockWebServer URL로 ClaudeService를 초기화
        // 실제로는 baseUrl을 외부화해야 하지만, 현재 구조에서는 통합테스트로 대체
    }

    @AfterEach
    void tearDown() throws IOException {
        mockServer.shutdown();
    }

    @Test
    void ask_정상응답_파싱() throws Exception {
        // Claude API 응답 형식
        Map<String, Object> response = Map.of(
                "content", List.of(Map.of("type", "text", "text", "테스트 응답입니다.")),
                "model", "claude-haiku-4-5-20251001",
                "role", "assistant"
        );
        String json = new ObjectMapper().writeValueAsString(response);
        mockServer.enqueue(new MockResponse()
                .setBody(json)
                .addHeader("Content-Type", "application/json"));

        // MockWebServer URL로 ClaudeService 생성
        ClaudeService service = createServiceWithMockServer();
        String result = service.ask("테스트 프롬프트");

        assertThat(result).isEqualTo("테스트 응답입니다.");

        RecordedRequest request = mockServer.takeRequest();
        assertThat(request.getPath()).isEqualTo("/v1/messages");
        assertThat(request.getHeader("x-api-key")).isEqualTo("test-key");
    }

    @Test
    void ask_응답에_content_없으면_예외() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setBody("{\"error\": \"invalid\"}")
                .addHeader("Content-Type", "application/json"));

        ClaudeService service = createServiceWithMockServer();
        assertThatThrownBy(() -> service.ask("프롬프트"))
                .isInstanceOf(RuntimeException.class);
    }

    private ClaudeService createServiceWithMockServer() throws Exception {
        String baseUrl = mockServer.url("/").toString();
        // 리플렉션으로 webClient의 baseUrl 교체
        ClaudeService service = new ClaudeService("test-key", "claude-haiku-4-5-20251001");

        var field = ClaudeService.class.getDeclaredField("webClient");
        field.setAccessible(true);

        var webClient = org.springframework.web.reactive.function.client.WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("x-api-key", "test-key")
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("content-type", "application/json")
                .build();
        field.set(service, webClient);

        return service;
    }
}
