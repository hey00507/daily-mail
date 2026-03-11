package com.dailymail.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DiscordServiceTest {

    @Test
    void 토큰이_비어있으면_비활성화() {
        DiscordService service = new DiscordService("", "");
        assertThat(service.isEnabled()).isFalse();
    }

    @Test
    void 토큰과_채널ID_있으면_활성화() {
        DiscordService service = new DiscordService("test-token", "123456");
        assertThat(service.isEnabled()).isTrue();
    }

    @Test
    void 비활성화_상태면_send_스킵() {
        DiscordService service = new DiscordService("", "");
        var content = new MailContent("[CS] 테스트", "cs-daily", Map.of("content", "테스트 내용"));
        // 예외 없이 정상 리턴
        service.send(content);
    }
}
