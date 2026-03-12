package com.dailymail.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "google.calendar")
public record GoogleCalendarConfig(
        String clientId,
        String clientSecret,
        String refreshToken
) {
    public GoogleCalendarConfig {
        if (clientId == null) clientId = "";
        if (clientSecret == null) clientSecret = "";
        if (refreshToken == null) refreshToken = "";
    }

    public boolean isConfigured() {
        return !clientId.isBlank() && !clientSecret.isBlank() && !refreshToken.isBlank();
    }
}
