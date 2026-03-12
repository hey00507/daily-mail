package com.dailymail.today;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarServiceTest {

    // --- isConfigured ---

    @Test
    void credentials가_비어있으면_미설정() {
        CalendarService service = new CalendarService("", "", "");
        assertThat(service.isConfigured()).isFalse();
    }

    @Test
    void credentials가_일부만_있으면_미설정() {
        CalendarService service = new CalendarService("client-id", "", "");
        assertThat(service.isConfigured()).isFalse();
    }

    @Test
    void credentials가_모두_있으면_설정됨() {
        CalendarService service = new CalendarService("id", "secret", "token");
        assertThat(service.isConfigured()).isTrue();
    }

    // --- getTodayEvents (인증 없는 경우) ---

    @Test
    void 미설정이면_빈_리스트() {
        CalendarService service = new CalendarService("", "", "");
        List<CalendarService.CalendarEvent> events = service.getTodayEvents();
        assertThat(events).isEmpty();
    }

    // --- toCalendarEvent (단위 테스트) ---

    @Test
    void toCalendarEvent_시간_이벤트_변환() {
        CalendarService service = new CalendarService("", "", "");

        // KST 10:30에 해당하는 밀리초
        LocalDateTime kst1030 = LocalDate.now().atTime(10, 30);
        long millis = kst1030.atZone(CalendarService.KST).toInstant().toEpochMilli();

        Event googleEvent = new Event()
                .setSummary("팀 미팅")
                .setLocation("회의실 A")
                .setHtmlLink("https://calendar.google.com/event/123")
                .setStart(new EventDateTime().setDateTime(new DateTime(millis)));

        CalendarService.CalendarEvent result = service.toCalendarEvent(googleEvent);

        assertThat(result.title()).isEqualTo("팀 미팅");
        assertThat(result.startTime()).isEqualTo("10:30");
        assertThat(result.allDay()).isFalse();
        assertThat(result.location()).isEqualTo("회의실 A");
        assertThat(result.link()).isEqualTo("https://calendar.google.com/event/123");
    }

    @Test
    void toCalendarEvent_종일_이벤트_변환() {
        CalendarService service = new CalendarService("", "", "");

        Event googleEvent = new Event()
                .setSummary("공휴일")
                .setStart(new EventDateTime().setDate(new DateTime("2026-03-12")));

        CalendarService.CalendarEvent result = service.toCalendarEvent(googleEvent);

        assertThat(result.title()).isEqualTo("공휴일");
        assertThat(result.startTime()).isEqualTo("종일");
        assertThat(result.allDay()).isTrue();
        assertThat(result.location()).isNull();
    }

    @Test
    void toCalendarEvent_제목_없으면_기본값() {
        CalendarService service = new CalendarService("", "", "");

        Event googleEvent = new Event()
                .setStart(new EventDateTime().setDate(new DateTime("2026-03-12")));

        CalendarService.CalendarEvent result = service.toCalendarEvent(googleEvent);

        assertThat(result.title()).isEqualTo("(제목 없음)");
    }

    @Test
    void toCalendarEvent_위치_없는_이벤트() {
        CalendarService service = new CalendarService("", "", "");

        long millis = LocalDate.now().atTime(14, 0)
                .atZone(CalendarService.KST).toInstant().toEpochMilli();

        Event googleEvent = new Event()
                .setSummary("온라인 미팅")
                .setStart(new EventDateTime().setDateTime(new DateTime(millis)));

        CalendarService.CalendarEvent result = service.toCalendarEvent(googleEvent);

        assertThat(result.title()).isEqualTo("온라인 미팅");
        assertThat(result.startTime()).isEqualTo("14:00");
        assertThat(result.location()).isNull();
    }

    // --- toCalendarEvents (여러 이벤트) ---

    @Test
    void toCalendarEvents_여러_이벤트_변환() {
        CalendarService service = new CalendarService("", "", "");

        long millis1 = LocalDate.now().atTime(9, 0)
                .atZone(CalendarService.KST).toInstant().toEpochMilli();
        long millis2 = LocalDate.now().atTime(15, 30)
                .atZone(CalendarService.KST).toInstant().toEpochMilli();

        List<Event> googleEvents = List.of(
                new Event().setSummary("아침 스탠드업")
                        .setStart(new EventDateTime().setDateTime(new DateTime(millis1))),
                new Event().setSummary("종일 워크숍")
                        .setStart(new EventDateTime().setDate(new DateTime("2026-03-12"))),
                new Event().setSummary("오후 미팅")
                        .setLocation("3층")
                        .setStart(new EventDateTime().setDateTime(new DateTime(millis2)))
        );

        List<CalendarService.CalendarEvent> results = service.toCalendarEvents(googleEvents);

        assertThat(results).hasSize(3);
        assertThat(results.get(0).title()).isEqualTo("아침 스탠드업");
        assertThat(results.get(0).startTime()).isEqualTo("09:00");
        assertThat(results.get(1).allDay()).isTrue();
        assertThat(results.get(2).location()).isEqualTo("3층");
    }

    // --- toDateTime ---

    @Test
    void toDateTime_변환() {
        LocalDateTime ldt = LocalDateTime.of(2026, 3, 12, 8, 0);
        DateTime result = CalendarService.toDateTime(ldt);

        long expected = ldt.atZone(ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli();
        assertThat(result.getValue()).isEqualTo(expected);
    }

    // --- CalendarEvent record ---

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
}
