package com.dailymail.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "mail")
public record MailConfig(
        String recipient,
        String module,
        Map<String, ModuleConfig> modules
) {
    public record ModuleConfig(
            boolean enabled,
            List<String> tags,
            Map<String, Integer> categoryWeights,
            boolean skipIfEmpty
    ) {
        public ModuleConfig {
            if (tags == null) tags = List.of();
            if (categoryWeights == null) categoryWeights = Map.of();
        }
    }
}
