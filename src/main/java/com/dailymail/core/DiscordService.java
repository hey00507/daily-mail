package com.dailymail.core;

import com.dailymail.config.DiscordConfig;
import com.dailymail.news.NewsBriefMail.SummarizedNews;
import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class DiscordService {

    private static final int DISCORD_MAX_LENGTH = 2000;

    private final WebClient webClient;
    private final DiscordConfig config;

    public DiscordService(
            DiscordConfig config,
            @Value("${discord.base-url:https://discord.com/api/v10}") String baseUrl
    ) {
        this.config = config;
        this.webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create().option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)))
                .baseUrl(baseUrl)
                .build();
    }

    public boolean isEnabled() {
        return !config.botToken().isBlank()
                && (!config.channelId().isBlank() || !config.channels().isEmpty());
    }

    private String resolveChannelId(String template) {
        String channelId = config.channels().get(template);
        return (channelId != null && !channelId.isBlank()) ? channelId : config.channelId();
    }

    private static final List<String> DISCORD_TEMPLATES = List.of("news-brief", "cs-daily");

    public void send(MailContent content) {
        if (!isEnabled()) {
            log.debug("Discord 설정 없음, 스킵");
            return;
        }

        if (!DISCORD_TEMPLATES.contains(content.template())) {
            log.debug("Discord 대상 아님, 스킵: {}", content.template());
            return;
        }

        try {
            String targetChannelId = resolveChannelId(content.template());
            String message = toDiscordMessage(content);
            List<String> chunks = splitMessage(message);

            for (String chunk : chunks) {
                webClient.post()
                        .uri("/channels/{channelId}/messages", targetChannelId)
                        .header("Authorization", "Bot " + config.botToken())
                        .header("Content-Type", "application/json")
                        .bodyValue(Map.of("content", chunk))
                        .retrieve()
                        .bodyToMono(String.class)
                        .block(Duration.ofSeconds(10));
            }

            log.info("Discord 발송 완료: {}", content.subject());
        } catch (WebClientResponseException e) {
            throw new DiscordSendException(
                    "Discord API 응답 오류 (HTTP %d): %s".formatted(e.getStatusCode().value(), e.getResponseBodyAsString()),
                    e.getStatusCode().value(),
                    e
            );
        } catch (Exception e) {
            throw new DiscordSendException("Discord 발송 중 오류: " + e.getMessage(), e);
        }
    }

    private String toDiscordMessage(MailContent content) {
        var sb = new StringBuilder();
        sb.append("## ").append(content.subject()).append("\n\n");

        var variables = content.variables();

        switch (content.template()) {
            case "news-brief" -> formatNewsBrief(sb, variables);
            case "cs-daily" -> formatCsDaily(sb, variables);
        }

        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private void formatNewsBrief(StringBuilder sb, Map<String, Object> variables) {
        var newsMap = (Map<String, List<SummarizedNews>>) variables.get("newsMap");
        if (newsMap == null) return;

        for (var entry : newsMap.entrySet()) {
            sb.append("### ").append(entry.getKey()).append("\n");
            int i = 1;
            for (SummarizedNews news : entry.getValue()) {
                sb.append(i++).append(". **").append(news.title()).append("**");
                if (!news.summary().isEmpty()) sb.append(" — ").append(news.summary());
                sb.append(" (").append(news.source()).append(")");
                if (!news.link().isEmpty()) sb.append("\n   ").append(news.link());
                sb.append("\n");
            }
            sb.append("\n");
        }
    }

    private void formatCsDaily(StringBuilder sb, Map<String, Object> variables) {
        var markdown = variables.get("contentMarkdown");
        if (markdown != null) {
            sb.append(markdown);
        }
    }

    private List<String> splitMessage(String message) {
        if (message.length() <= DISCORD_MAX_LENGTH) {
            return List.of(message);
        }

        var chunks = new ArrayList<String>();
        int start = 0;
        while (start < message.length()) {
            int end = Math.min(start + DISCORD_MAX_LENGTH, message.length());
            if (end < message.length()) {
                int lastNewline = message.lastIndexOf('\n', end);
                if (lastNewline > start) {
                    end = lastNewline + 1;
                }
            }
            chunks.add(message.substring(start, end));
            start = end;
        }
        return chunks;
    }
}
