package com.dailymail.core;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MailContentTest {

    @Test
    void record_생성_및_접근() {
        var content = new MailContent(
                "[CS] 03/10 — OS: 프로세스 vs 스레드",
                "cs-daily",
                Map.of("category", "OS", "topic", "프로세스 vs 스레드")
        );

        assertThat(content.subject()).contains("CS");
        assertThat(content.template()).isEqualTo("cs-daily");
        assertThat(content.variables()).containsEntry("category", "OS");
    }
}
