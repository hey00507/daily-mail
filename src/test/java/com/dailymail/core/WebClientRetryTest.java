package com.dailymail.core;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import static org.assertj.core.api.Assertions.assertThat;

class WebClientRetryTest {

    @Test
    void isTransient_5xx이면_true() {
        var exception = WebClientResponseException.create(500, "Internal Server Error", null, null, null);
        assertThat(WebClientRetry.isTransient(exception)).isTrue();
    }

    @Test
    void isTransient_4xx이면_false() {
        var exception = WebClientResponseException.create(400, "Bad Request", null, null, null);
        assertThat(WebClientRetry.isTransient(exception)).isFalse();
    }

    @Test
    void isTransient_기타_예외는_true() {
        assertThat(WebClientRetry.isTransient(new RuntimeException("connection refused"))).isTrue();
    }
}
