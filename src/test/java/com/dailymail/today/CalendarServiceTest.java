package com.dailymail.today;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarServiceTest {

    @Test
    void credentials가_비어있으면_빈_리스트() {
        CalendarService service = new CalendarService("", "", "");
        List<CalendarService.CalendarEvent> events = service.getTodayEvents();
        assertThat(events).isEmpty();
    }

    @Test
    void credentials가_일부만_있으면_빈_리스트() {
        CalendarService service = new CalendarService("client-id", "", "");
        List<CalendarService.CalendarEvent> events = service.getTodayEvents();
        assertThat(events).isEmpty();
    }

    @Test
    void CalendarEvent_record_생성() {
        var event = new CalendarService.CalendarEvent(
                "팀 미팅", "10:00", false, "회의실 A", "https://calendar.google.com/..."
        );
        assertThat(event.title()).isEqualTo("팀 미팅");
        assertThat(event.startTime()).isEqualTo("10:00");
        assertThat(event.allDay()).isFalse();
        assertThat(event.location()).isEqualTo("회의실 A");
    }

    @Test
    void CalendarEvent_종일이벤트() {
        var event = new CalendarService.CalendarEvent(
                "공휴일", "종일", true, null, null
        );
        assertThat(event.allDay()).isTrue();
        assertThat(event.location()).isNull();
    }
}
