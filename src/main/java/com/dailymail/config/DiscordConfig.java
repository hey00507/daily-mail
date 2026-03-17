package com.dailymail.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "discord")
public record DiscordConfig(
        boolean enabled,
        String botToken,
        String channelId,
        Map<String, String> channels
) {
    public DiscordConfig {
        if (botToken == null) botToken = "";
        if (channelId == null) channelId = "";
        if (channels == null) channels = Map.of();
    }
}
