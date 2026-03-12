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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
        var moduleConfig = new MailConfig.ModuleConfig(true, false);
        when(mailConfig.modules()).thenReturn(Map.of("cs-daily", moduleConfig));

        assertThat(csDailyMail.isEnabled()).isTrue();
    }

    @Test
    void isEnabled_설정이_없으면_비활성화() {
        when(mailConfig.modules()).thenReturn(Map.of());

        assertThat(csDailyMail.isEnabled()).isFalse();
    }

    @Test
    void isEnabled_설정이_false면_비활성화() {
        var moduleConfig = new MailConfig.ModuleConfig(false, false);
        when(mailConfig.modules()).thenReturn(Map.of("cs-daily", moduleConfig));

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
        when(historyService.getSentTopics("cs-daily"))
                .thenReturn(List.of("OS: 프로세스 vs 스레드"));
        when(claudeService.ask(anyString())).thenReturn("응답");

        MailContent content = csDailyMail.generate();

        assertThat(content).isNotNull();
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

    @Test
    void generate_모든_주제_소진시_리셋하여_재선택() {
        // 모든 토픽을 "이미 보낸 것"으로 설정
        List<String> allTopics = new ArrayList<>();
        for (var entry : CsDailyMail.CATEGORIES.entrySet()) {
            for (String topic : entry.getValue()) {
                allTopics.add(entry.getKey() + ": " + topic);
            }
        }

        when(historyService.getSentTopics("cs-daily")).thenReturn(allTopics);
        when(claudeService.ask(anyString())).thenReturn("## 면접 질문\n리셋 후 응답");

        MailContent content = csDailyMail.generate();

        assertThat(content).isNotNull();
        assertThat(content.subject()).contains("[CS]");
        // 리셋 후에도 정상적으로 주제를 선택하고 콘텐츠를 생성
        verify(historyService).append(eq("cs-daily"), anyString());
    }
}
