package com.dailymail.core;

import java.util.Map;

public record MailContent(
        String subject,
        String template,
        Map<String, Object> variables
) {
}
