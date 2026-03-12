package com.dailymail.core;

import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import java.time.Duration;
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
        this(WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create().option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)))
                .baseUrl("https://api.anthropic.com")
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("content-type", "application/json")
                .build(), model);
    }

    ClaudeService(WebClient webClient, String model) {
        this.webClient = webClient;
        this.model = model;
    }

    public String ask(String prompt) {
        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", 2048,
                "messages", List.of(
                        Map.of("role", "user", "content", prompt)
                )
        );

        Map<String, Object> response;
        try {
            response = webClient.post()
                    .uri("/v1/messages")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .retryWhen(Retry.backoff(1, Duration.ofSeconds(1))
                            .filter(ClaudeService::isTransient))
                    .block(Duration.ofSeconds(30));
        } catch (WebClientResponseException e) {
            log.error("Claude API 에러 [{}]: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        }

        if (response == null || !response.containsKey("content")) {
            throw new RuntimeException("Claude API 응답 파싱 실패");
        }

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
        return (String) content.getFirst().get("text");
    }

    static boolean isTransient(Throwable t) {
        if (t instanceof WebClientResponseException e) {
            return e.getStatusCode().is5xxServerError();
        }
        return true;
    }
}
