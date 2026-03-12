package com.dailymail.config;

import com.dailymail.config.DiscordConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MailConfigTest {

    // --- DiscordConfig null 파라미터 브랜치 ---

    @Test
    void DiscordConfig_null_파라미터_기본값() {
        var config = new DiscordConfig(null, null, null);
        assertThat(config.botToken()).isEmpty();
        assertThat(config.channelId()).isEmpty();
        assertThat(config.channels()).isEmpty();
    }

    @Test
    void DiscordConfig_일부만_null() {
        var config = new DiscordConfig("token", null, null);
        assertThat(config.botToken()).isEqualTo("token");
        assertThat(config.channelId()).isEmpty();
        assertThat(config.channels()).isEmpty();
    }

    @Test
    void 모듈설정_기본값_처리() {
        var config = new MailConfig.ModuleConfig(true, null, null, false);

        assertThat(config.tags()).isEmpty();
        assertThat(config.categoryWeights()).isEmpty();
        assertThat(config.enabled()).isTrue();
    }

    @Test
    void 모듈설정_값이_있으면_그대로() {
        var config = new MailConfig.ModuleConfig(
                true, List.of("tech", "경제"), Map.of("OS", 2), true
        );

        assertThat(config.tags()).containsExactly("tech", "경제");
        assertThat(config.categoryWeights()).containsEntry("OS", 2);
        assertThat(config.skipIfEmpty()).isTrue();
    }

    @Test
    void MailConfig_record_생성() {
        var moduleConfig = new MailConfig.ModuleConfig(true, null, null, false);
        var mailConfig = new MailConfig("test@gmail.com", "all", Map.of("cs-daily", moduleConfig));

        assertThat(mailConfig.recipient()).isEqualTo("test@gmail.com");
        assertThat(mailConfig.module()).isEqualTo("all");
        assertThat(mailConfig.modules()).containsKey("cs-daily");
    }
}
