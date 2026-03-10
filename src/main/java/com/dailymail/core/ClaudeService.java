package com.dailymail.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ClaudeService {

    private final WebClient webClient;
    private final String model;

    public ClaudeService(
            @Value("${claude.api-key}") String apiKey,
            @Value("${claude.model}") String model
    ) {
        this.model = model;
        this.webClient = WebClient.builder()
                .baseUrl("https://api.anthropic.com")
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("content-type", "application/json")
                .build();
    }

    public String ask(String prompt) {
        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", 2048,
                "messages", List.of(
                        Map.of("role", "user", "content", prompt)
                )
        );

        Map<String, Object> response = webClient.post()
                .uri("/v1/messages")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null || !response.containsKey("content")) {
            throw new RuntimeException("Claude API 응답 파싱 실패");
        }

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
        return (String) content.getFirst().get("text");
    }
}
