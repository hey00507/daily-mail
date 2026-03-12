package com.dailymail.core;

import com.dailymail.config.DiscordConfig;
import com.dailymail.news.NewsBriefMail;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DiscordServiceTest {

    // --- isEnabled ---

    @Test
    void 토큰이_비어있으면_비활성화() {
        var config = new DiscordConfig("", "", Map.of());
        var service = new DiscordService(config);
        assertThat(service.isEnabled()).isFalse();
    }

    @Test
    void 토큰과_기본채널ID_있으면_활성화() {
        var config = new DiscordConfig("test-token", "123456", Map.of());
        var service = new DiscordService(config);
        assertThat(service.isEnabled()).isTrue();
    }

    @Test
    void 기본채널ID_없어도_모듈별_채널_있으면_활성화() {
        var config = new DiscordConfig("test-token", "", Map.of("cs-daily", "111"));
        var service = new DiscordService(config);
        assertThat(service.isEnabled()).isTrue();
    }

    @Test
    void 토큰_없으면_모듈별_채널_있어도_비활성화() {
        var config = new DiscordConfig("", "", Map.of("cs-daily", "111"));
        var service = new DiscordService(config);
        assertThat(service.isEnabled()).isFalse();
    }

    // --- send 스킵 조건 ---

    @Test
    void 비활성화_상태면_send_스킵() {
        var config = new DiscordConfig("", "", Map.of());
        var service = new DiscordService(config);
        var content = new MailContent("[CS] 테스트", "cs-daily", Map.of("contentMarkdown", "테스트"));
        // 예외 없이 정상 리턴
        service.send(content);
    }

    @Test
    void 지원하지_않는_템플릿이면_send_스킵() {
        var config = new DiscordConfig("test-token", "123", Map.of());
        var service = new DiscordService(config);
        var content = new MailContent("[Today] 테스트", "today-brief", Map.of());
        // today-brief는 DISCORD_TEMPLATES에 없으므로 스킵, 예외 없음
        service.send(content);
    }

    // --- toDiscordMessage 포맷팅 ---

    @Test
    void toDiscordMessage_cs_daily_마크다운_포맷() {
        var config = new DiscordConfig("token", "123", Map.of());
        var service = new DiscordService(config);

        var content = new MailContent("[CS] OS: 프로세스", "cs-daily", Map.of(
                "contentMarkdown", "## 면접 질문\n프로세스와 스레드의 차이는?"
        ));

        String message = service.toDiscordMessage(content);

        assertThat(message).contains("## [CS] OS: 프로세스");
        assertThat(message).contains("## 면접 질문");
        assertThat(message).contains("프로세스와 스레드의 차이는?");
    }

    @Test
    void toDiscordMessage_cs_daily_contentMarkdown_없으면_빈_본문() {
        var config = new DiscordConfig("token", "123", Map.of());
        var service = new DiscordService(config);

        var content = new MailContent("[CS] 테스트", "cs-daily", Map.of());

        String message = service.toDiscordMessage(content);

        assertThat(message).contains("## [CS] 테스트");
        // contentMarkdown이 없으면 헤더만 나옴
        assertThat(message.trim()).isEqualTo("## [CS] 테스트");
    }

    @Test
    void toDiscordMessage_news_brief_포맷() {
        var config = new DiscordConfig("token", "123", Map.of());
        var service = new DiscordService(config);

        var newsMap = new LinkedHashMap<String, List<?>>();
        newsMap.put("IT", List.of(
                new NewsBriefMail.SummarizedNews("AI 뉴스", "AI 요약", "https://example.com", "조선일보", "IT")
        ));

        var content = new MailContent("[News] 03/12", "news-brief", Map.of("newsMap", newsMap));

        String message = service.toDiscordMessage(content);

        assertThat(message).contains("### IT");
        assertThat(message).contains("**AI 뉴스**");
        assertThat(message).contains("AI 요약");
        assertThat(message).contains("조선일보");
        assertThat(message).contains("https://example.com");
    }

    @Test
    void toDiscordMessage_news_brief_newsMap_null이면_헤더만() {
        var config = new DiscordConfig("token", "123", Map.of());
        var service = new DiscordService(config);

        var content = new MailContent("[News] 테스트", "news-brief", Map.of());

        String message = service.toDiscordMessage(content);

        assertThat(message).contains("## [News] 테스트");
    }

    @Test
    void toDiscordMessage_알수없는_템플릿() {
        var config = new DiscordConfig("token", "123", Map.of());
        var service = new DiscordService(config);

        var content = new MailContent("제목", "unknown-template", Map.of());

        String message = service.toDiscordMessage(content);

        assertThat(message).contains("알 수 없는 템플릿: unknown-template");
    }

    // --- splitMessage ---

    @Test
    void splitMessage_2000자_이하면_분할_안함() {
        var config = new DiscordConfig("token", "123", Map.of());
        var service = new DiscordService(config);

        String short_message = "짧은 메시지";
        List<String> chunks = service.splitMessage(short_message);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).isEqualTo(short_message);
    }

    @Test
    void splitMessage_2000자_초과시_줄바꿈_기준_분할() {
        var config = new DiscordConfig("token", "123", Map.of());
        var service = new DiscordService(config);

        // 각 줄을 50자로 만들어 총 2500자 이상 생성
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            sb.append("Line ").append(String.format("%02d", i))
              .append(": ").append("x".repeat(40)).append("\n");
        }
        String longMessage = sb.toString();
        assertThat(longMessage.length()).isGreaterThan(2000);

        List<String> chunks = service.splitMessage(longMessage);

        assertThat(chunks.size()).isGreaterThan(1);
        for (String chunk : chunks) {
            assertThat(chunk.length()).isLessThanOrEqualTo(2000);
        }
        assertThat(String.join("", chunks)).isEqualTo(longMessage);
    }

    // --- DiscordSendException ---

    @Test
    void DiscordSendException_상태코드_포함() {
        var ex = new DiscordSendException("404 에러", 404, new RuntimeException("cause"));
        assertThat(ex.getStatusCode()).isEqualTo(404);
        assertThat(ex.getMessage()).isEqualTo("404 에러");
        assertThat(ex.getCause()).isInstanceOf(RuntimeException.class);
    }

    @Test
    void DiscordSendException_상태코드_없는_생성자() {
        var ex = new DiscordSendException("일반 에러", new RuntimeException("cause"));
        assertThat(ex.getStatusCode()).isEqualTo(0);
        assertThat(ex.getMessage()).isEqualTo("일반 에러");
    }
}
