package com.dailymail.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;

class MailRunnerTest {

    @Test
    void all이면_활성화된_모듈_전부_실행() {
        MailModule cs = mockModule("cs-daily", true,
                new MailContent("[CS] 테스트", "cs-daily", Map.of()));
        MailModule news = mockModule("news-brief", true,
                new MailContent("[News] 테스트", "news-brief", Map.of()));
        MailService mailService = mock(MailService.class);

        MailRunner runner = new MailRunner(List.of(cs, news), mailService, "all");
        runner.run();

        verify(mailService, times(2)).send(any(MailContent.class));
    }

    @Test
    void 특정_모듈만_실행() {
        MailModule cs = mockModule("cs-daily", true,
                new MailContent("[CS] 테스트", "cs-daily", Map.of()));
        MailModule news = mockModule("news-brief", true,
                new MailContent("[News] 테스트", "news-brief", Map.of()));
        MailService mailService = mock(MailService.class);

        MailRunner runner = new MailRunner(List.of(cs, news), mailService, "cs-daily");
        runner.run();

        verify(mailService, times(1)).send(any(MailContent.class));
        verify(cs).generate();
        verify(news, never()).generate();
    }

    @Test
    void 비활성화_모듈은_스킵() {
        MailModule cs = mockModule("cs-daily", false, null);
        MailService mailService = mock(MailService.class);

        MailRunner runner = new MailRunner(List.of(cs), mailService, "all");
        runner.run();

        verify(cs, never()).generate();
        verify(mailService, never()).send(any());
    }

    @Test
    void generate가_null이면_발송_안함() {
        MailModule today = mockModule("today-brief", true, null);
        MailService mailService = mock(MailService.class);

        MailRunner runner = new MailRunner(List.of(today), mailService, "all");
        runner.run();

        verify(today).generate();
        verify(mailService, never()).send(any());
    }

    @Test
    void 모듈_에러_발생해도_다른_모듈_계속_실행() {
        MailModule failing = mock(MailModule.class);
        when(failing.name()).thenReturn("failing");
        when(failing.isEnabled()).thenReturn(true);
        when(failing.generate()).thenThrow(new RuntimeException("테스트 에러"));

        MailModule cs = mockModule("cs-daily", true,
                new MailContent("[CS] 테스트", "cs-daily", Map.of()));
        MailService mailService = mock(MailService.class);

        MailRunner runner = new MailRunner(List.of(failing, cs), mailService, "all");
        runner.run();

        verify(mailService, times(1)).send(any(MailContent.class));
    }

    private MailModule mockModule(String name, boolean enabled, MailContent content) {
        MailModule module = mock(MailModule.class);
        when(module.name()).thenReturn(name);
        when(module.isEnabled()).thenReturn(enabled);
        if (enabled) {
            when(module.generate()).thenReturn(content);
        }
        return module;
    }
}
