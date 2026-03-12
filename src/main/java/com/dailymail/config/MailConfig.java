package com.dailymail.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "mail")
public record MailConfig(
        String recipient,
        String module,
        Map<String, ModuleConfig> modules
) {
    public record ModuleConfig(
            boolean enabled,
            boolean skipIfEmpty
    ) {}
}
