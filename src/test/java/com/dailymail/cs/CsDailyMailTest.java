package com.dailymail.cs;

import com.dailymail.config.MailConfig;
import com.dailymail.core.ClaudeService;
import com.dailymail.core.HistoryService;
import com.dailymail.core.MailContent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CsDailyMailTest {

    @Mock
    ClaudeService claudeService;

    @Mock
    HistoryService historyService;

    @Mock
    MailConfig mailConfig;

    @InjectMocks
    CsDailyMail csDailyMail;

    @Test
    void name은_cs_daily() {
        assertThat(csDailyMail.name()).isEqualTo("cs-daily");
    }

    @Test
    void isEnabled_설정이_true면_활성화() {
        var moduleConfig = new MailConfig.ModuleConfig(true, null, null, false);
        when(mailConfig.modules()).thenReturn(Map.of("cs-daily", moduleConfig));

        assertThat(csDailyMail.isEnabled()).isTrue();
    }

    @Test
    void isEnabled_설정이_없으면_비활성화() {
        when(mailConfig.modules()).thenReturn(Map.of());

        assertThat(csDailyMail.isEnabled()).isFalse();
    }

    @Test
    void generate_정상_콘텐츠_생성() {
        when(historyService.getSentTopics("cs-daily")).thenReturn(List.of());
        when(claudeService.ask(anyString())).thenReturn("## 면접 질문\n테스트 응답");

        MailContent content = csDailyMail.generate();

        assertThat(content).isNotNull();
        assertThat(content.subject()).contains("[CS]");
        assertThat(content.template()).isEqualTo("cs-daily");
        assertThat(content.variables()).containsKey("category");
        assertThat(content.variables()).containsKey("topic");
        assertThat(content.variables()).containsKey("content");

        verify(historyService).append(eq("cs-daily"), anyString());
        verify(claudeService).ask(anyString());
    }

    @Test
    void generate_이미_보낸_주제는_제외() {
        // OS 카테고리의 첫 번째 주제를 이미 보낸 것으로 설정
        when(historyService.getSentTopics("cs-daily"))
                .thenReturn(List.of("OS: 프로세스 vs 스레드"));
        when(claudeService.ask(anyString())).thenReturn("응답");

        MailContent content = csDailyMail.generate();

        assertThat(content).isNotNull();
        // 보낸 주제가 아닌 다른 주제가 선택되어야 함
        verify(historyService).append(eq("cs-daily"), anyString());
    }

    @Test
    void generate_프롬프트에_카테고리와_주제_포함() {
        when(historyService.getSentTopics("cs-daily")).thenReturn(List.of());

        var promptCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        when(claudeService.ask(promptCaptor.capture())).thenReturn("응답");

        csDailyMail.generate();

        String prompt = promptCaptor.getValue();
        assertThat(prompt).contains("카테고리:");
        assertThat(prompt).contains("주제:");
        assertThat(prompt).contains("면접");
        assertThat(prompt).contains("마크다운");
    }
}
