package com.dailymail.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class DiscordService {

    private static final int DISCORD_MAX_LENGTH = 2000;

    private final WebClient webClient;
    private final String botToken;
    private final String channelId;

    public DiscordService(
            @Value("${discord.bot-token:}") String botToken,
            @Value("${discord.channel-id:}") String channelId
    ) {
        this.botToken = botToken;
        this.channelId = channelId;
        this.webClient = WebClient.builder()
                .baseUrl("https://discord.com/api/v10")
                .build();
    }

    public boolean isEnabled() {
        return !botToken.isBlank() && !channelId.isBlank();
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
            String message = toDiscordMessage(content);
            List<String> chunks = splitMessage(message);

            for (String chunk : chunks) {
                webClient.post()
                        .uri("/channels/{channelId}/messages", channelId)
                        .header("Authorization", "Bot " + botToken)
                        .header("Content-Type", "application/json")
                        .bodyValue(Map.of("content", chunk))
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();
            }

            log.info("Discord 발송 완료: {}", content.subject());
        } catch (Exception e) {
            log.error("Discord 발송 실패: {}", e.getMessage());
        }
    }

    private String toDiscordMessage(MailContent content) {
        var sb = new StringBuilder();
        sb.append("## ").append(content.subject()).append("\n\n");

        var variables = content.variables();

        switch (content.template()) {
            case "news-brief" -> formatNewsBrief(sb, variables);
            case "cs-daily" -> formatCsDaily(sb, variables);
            case "today-brief" -> formatTodayBrief(sb, variables);
            default -> sb.append("(알 수 없는 템플릿: ").append(content.template()).append(")");
        }

        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private void formatNewsBrief(StringBuilder sb, Map<String, Object> variables) {
        var newsMap = (Map<String, List<?>>) variables.get("newsMap");
        if (newsMap == null) return;

        for (var entry : newsMap.entrySet()) {
            sb.append("### ").append(entry.getKey()).append("\n");
            int i = 1;
            for (var item : entry.getValue()) {
                try {
                    var title = getField(item, "title");
                    var summary = getField(item, "summary");
                    var link = getField(item, "link");
                    var source = getField(item, "source");

                    sb.append(i++).append(". **").append(title).append("**");
                    if (!summary.isEmpty()) sb.append(" — ").append(summary);
                    sb.append(" (").append(source).append(")");
                    if (!link.isEmpty()) sb.append("\n   ").append(link);
                    sb.append("\n");
                } catch (Exception e) {
                    log.warn("뉴스 항목 포맷 실패", e);
                }
            }
            sb.append("\n");
        }
    }

    private void formatCsDaily(StringBuilder sb, Map<String, Object> variables) {
        var content = variables.get("content");
        if (content != null) {
            sb.append(content);
        }
    }

    @SuppressWarnings("unchecked")
    private void formatTodayBrief(StringBuilder sb, Map<String, Object> variables) {
        var events = (List<?>) variables.get("events");
        var hasEvents = (Boolean) variables.getOrDefault("hasEvents", false);

        if (!hasEvents || events == null || events.isEmpty()) {
            sb.append("오늘 일정이 없습니다.");
            return;
        }

        for (var event : events) {
            try {
                var title = getField(event, "title");
                var startTime = getField(event, "startTime");
                var location = getField(event, "location");

                sb.append("- **").append(startTime).append("** ").append(title);
                if (location != null && !location.isEmpty()) {
                    sb.append(" (").append(location).append(")");
                }
                sb.append("\n");
            } catch (Exception e) {
                log.warn("일정 항목 포맷 실패", e);
            }
        }
    }

    private String getField(Object obj, String fieldName) {
        try {
            var method = obj.getClass().getMethod(fieldName);
            var result = method.invoke(obj);
            return result != null ? result.toString() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private List<String> splitMessage(String message) {
        if (message.length() <= DISCORD_MAX_LENGTH) {
            return List.of(message);
        }

        var chunks = new java.util.ArrayList<String>();
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
