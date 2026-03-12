package com.dailymail.core;

import com.dailymail.config.DiscordConfig;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DiscordServiceTest {

    @Test
    void 토큰이_비어있으면_비활성화() {
        var config = new DiscordConfig("", "", Map.of());
        DiscordService service = new DiscordService(config);
        assertThat(service.isEnabled()).isFalse();
    }

    @Test
    void 토큰과_채널ID_있으면_활성화() {
        var config = new DiscordConfig("test-token", "123456", Map.of());
        DiscordService service = new DiscordService(config);
        assertThat(service.isEnabled()).isTrue();
    }

    @Test
    void 비활성화_상태면_send_스킵() {
        var config = new DiscordConfig("", "", Map.of());
        DiscordService service = new DiscordService(config);
        var content = new MailContent("[CS] 테스트", "cs-daily", Map.of("content", "테스트 내용"));
        service.send(content);
    }
}
