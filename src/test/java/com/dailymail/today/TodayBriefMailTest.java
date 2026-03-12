package com.dailymail.today;

import com.dailymail.config.MailConfig;
import com.dailymail.core.MailContent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TodayBriefMailTest {

    @Mock
    CalendarService calendarService;

    @Mock
    MailConfig mailConfig;

    @InjectMocks
    TodayBriefMail todayBriefMail;

    @Test
    void name은_today_brief() {
        assertThat(todayBriefMail.name()).isEqualTo("today-brief");
    }

    @Test
    void isEnabled_설정이_true면_활성화() {
        var config = new MailConfig.ModuleConfig(true, null, null, true);
        when(mailConfig.modules()).thenReturn(Map.of("today-brief", config));

        assertThat(todayBriefMail.isEnabled()).isTrue();
    }

    @Test
    void isEnabled_설정이_없으면_비활성화() {
        when(mailConfig.modules()).thenReturn(Map.of());

        assertThat(todayBriefMail.isEnabled()).isFalse();
    }

    @Test
    void isEnabled_설정이_false면_비활성화() {
        var config = new MailConfig.ModuleConfig(false, null, null, false);
        when(mailConfig.modules()).thenReturn(Map.of("today-brief", config));

        assertThat(todayBriefMail.isEnabled()).isFalse();
    }

    @Test
    void generate_일정이_있으면_콘텐츠_반환() {
        var event = new CalendarService.CalendarEvent("팀 미팅", "10:00", false, "회의실", null);
        when(calendarService.getTodayEvents()).thenReturn(List.of(event));

        MailContent content = todayBriefMail.generate();

        assertThat(content).isNotNull();
        assertThat(content.subject()).contains("[Today]");
        assertThat(content.subject()).contains("1건");
        assertThat(content.template()).isEqualTo("today-brief");

        @SuppressWarnings("unchecked")
        List<CalendarService.CalendarEvent> events =
                (List<CalendarService.CalendarEvent>) content.variables().get("events");
        assertThat(events).hasSize(1);
        assertThat((Boolean) content.variables().get("hasEvents")).isTrue();
    }

    @Test
    void generate_일정없고_skipIfEmpty면_null() {
        when(calendarService.getTodayEvents()).thenReturn(List.of());
        var config = new MailConfig.ModuleConfig(true, null, null, true);
        when(mailConfig.modules()).thenReturn(Map.of("today-brief", config));

        MailContent content = todayBriefMail.generate();

        assertThat(content).isNull();
    }

    @Test
    void generate_일정없고_skipIfEmpty가_false면_빈일정_메일발송() {
        when(calendarService.getTodayEvents()).thenReturn(List.of());
        var config = new MailConfig.ModuleConfig(true, null, null, false);
        when(mailConfig.modules()).thenReturn(Map.of("today-brief", config));

        MailContent content = todayBriefMail.generate();

        assertThat(content).isNotNull();
        assertThat(content.subject()).contains("일정 없음");
        assertThat((Boolean) content.variables().get("hasEvents")).isFalse();
    }

    @Test
    void generate_일정없고_config_없으면_빈일정_메일발송() {
        when(calendarService.getTodayEvents()).thenReturn(List.of());
        when(mailConfig.modules()).thenReturn(Map.of());

        MailContent content = todayBriefMail.generate();

        assertThat(content).isNotNull();
        assertThat(content.subject()).contains("일정 없음");
    }

    @Test
    void generate_여러_일정() {
        var event1 = new CalendarService.CalendarEvent("아침 회의", "09:00", false, null, null);
        var event2 = new CalendarService.CalendarEvent("점심 약속", "12:00", false, "강남", null);
        var event3 = new CalendarService.CalendarEvent("공휴일", "종일", true, null, null);
        when(calendarService.getTodayEvents()).thenReturn(List.of(event1, event2, event3));

        MailContent content = todayBriefMail.generate();

        assertThat(content).isNotNull();
        assertThat(content.subject()).contains("3건");
    }
}
