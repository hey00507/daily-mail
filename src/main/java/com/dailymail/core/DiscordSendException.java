package com.dailymail.core;

public class DiscordSendException extends RuntimeException {

    private final int statusCode;

    public DiscordSendException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public DiscordSendException(String message, Throwable cause) {
        this(message, 0, cause);
    }

    public int getStatusCode() {
        return statusCode;
    }
}
