package com.dailymail.core;

import org.springframework.web.reactive.function.client.WebClientResponseException;

public final class WebClientRetry {

    private WebClientRetry() {}

    public static boolean isTransient(Throwable t) {
        if (t instanceof WebClientResponseException e) {
            return e.getStatusCode().is5xxServerError();
        }
        return true;
    }
}
