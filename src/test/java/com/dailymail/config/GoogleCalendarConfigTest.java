package com.dailymail.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleCalendarConfigTest {

    @Test
    void null_값은_빈_문자열로_치환() {
        var config = new GoogleCalendarConfig(null, null, null);
        assertThat(config.clientId()).isEmpty();
        assertThat(config.clientSecret()).isEmpty();
        assertThat(config.refreshToken()).isEmpty();
        assertThat(config.isConfigured()).isFalse();
    }

    @Test
    void 모든_값이_있으면_설정됨() {
        var config = new GoogleCalendarConfig("id", "secret", "token");
        assertThat(config.isConfigured()).isTrue();
    }

    @Test
    void 하나라도_빈_문자열이면_미설정() {
        assertThat(new GoogleCalendarConfig("id", "", "token").isConfigured()).isFalse();
        assertThat(new GoogleCalendarConfig("", "secret", "token").isConfigured()).isFalse();
        assertThat(new GoogleCalendarConfig("id", "secret", "").isConfigured()).isFalse();
    }
}
