package com.dailymail.config;

import com.dailymail.config.DiscordConfig;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MailConfigTest {

    // --- DiscordConfig null 파라미터 브랜치 ---

    @Test
    void DiscordConfig_null_파라미터_기본값() {
        var config = new DiscordConfig(false, null, null, null);
        assertThat(config.botToken()).isEmpty();
        assertThat(config.channelId()).isEmpty();
        assertThat(config.channels()).isEmpty();
    }

    @Test
    void DiscordConfig_일부만_null() {
        var config = new DiscordConfig(true, "token", null, null);
        assertThat(config.botToken()).isEqualTo("token");
        assertThat(config.channelId()).isEmpty();
        assertThat(config.channels()).isEmpty();
    }

    // --- ModuleConfig ---

    @Test
    void 모듈설정_enabled_true() {
        var config = new MailConfig.ModuleConfig(true, false);
        assertThat(config.enabled()).isTrue();
        assertThat(config.skipIfEmpty()).isFalse();
    }

    @Test
    void 모듈설정_skipIfEmpty_true() {
        var config = new MailConfig.ModuleConfig(true, true);
        assertThat(config.enabled()).isTrue();
        assertThat(config.skipIfEmpty()).isTrue();
    }

    // --- MailConfig ---

    @Test
    void MailConfig_record_생성() {
        var moduleConfig = new MailConfig.ModuleConfig(true, false);
        var mailConfig = new MailConfig("test@gmail.com", "all", Map.of("cs-daily", moduleConfig));

        assertThat(mailConfig.recipient()).isEqualTo("test@gmail.com");
        assertThat(mailConfig.module()).isEqualTo("all");
        assertThat(mailConfig.modules()).containsKey("cs-daily");
    }
}
