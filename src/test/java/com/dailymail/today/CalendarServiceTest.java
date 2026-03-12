package com.dailymail.today;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CalendarServiceTest {

    // --- isConfigured ---

    @Test
    void credentials가_비어있으면_미설정() {
        CalendarService service = new CalendarService("", "", "", () -> null);
        assertThat(service.isConfigured()).isFalse();
    }

    @Test
    void credentials가_clientSecret만_비어있으면_미설정() {
        CalendarService service = new CalendarService("client-id", "", "token", () -> null);
        assertThat(service.isConfigured()).isFalse();
    }

    @Test
    void credentials가_refreshToken만_비어있으면_미설정() {
        CalendarService service = new CalendarService("client-id", "secret", "", () -> null);
        assertThat(service.isConfigured()).isFalse();
    }

    @Test
    void credentials가_모두_있으면_설정됨() {
        CalendarService service = new CalendarService("id", "secret", "token", () -> null);
        assertThat(service.isConfigured()).isTrue();
    }

    // --- getTodayEvents ---

    @Test
    void 미설정이면_빈_리스트() {
        CalendarService service = new CalendarService("", "", "", () -> null);
        List<CalendarService.CalendarEvent> events = service.getTodayEvents();
        assertThat(events).isEmpty();
    }

    @Test
    void getTodayEvents_시간_이벤트() throws Exception {
        Calendar mockCalendar = mock(Calendar.class, RETURNS_DEEP_STUBS);

        long millis = LocalDate.now(CalendarService.KST).atTime(10, 30)
                .atZone(CalendarService.KST).toInstant().toEpochMilli();

        Events events = new Events().setItems(List.of(
                new Event()
                        .setSummary("팀 미팅")
                        .setLocation("회의실 A")
                        .setHtmlLink("https://calendar.google.com/event/123")
                        .setStart(new EventDateTime().setDateTime(new DateTime(millis)))
        ));

        when(mockCalendar.events().list(anyString())
                .setTimeMin(org.mockito.ArgumentMatchers.any())
                .setTimeMax(org.mockito.ArgumentMatchers.any())
                .setOrderBy(anyString())
                .setSingleEvents(true)
                .execute()
        ).thenReturn(events);

        CalendarService service = new CalendarService("id", "secret", "token", () -> mockCalendar);
        List<CalendarService.CalendarEvent> result = service.getTodayEvents();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("팀 미팅");
        assertThat(result.get(0).startTime()).isEqualTo("10:30");
        assertThat(result.get(0).allDay()).isFalse();
        assertThat(result.get(0).location()).isEqualTo("회의실 A");
        assertThat(result.get(0).link()).isEqualTo("https://calendar.google.com/event/123");
    }

    @Test
    void getTodayEvents_종일_이벤트() throws Exception {
        Calendar mockCalendar = mock(Calendar.class, RETURNS_DEEP_STUBS);

        Events events = new Events().setItems(List.of(
                new Event()
                        .setSummary("공휴일")
                        .setStart(new EventDateTime().setDate(new DateTime("2026-03-12")))
        ));

        when(mockCalendar.events().list(anyString())
                .setTimeMin(org.mockito.ArgumentMatchers.any())
                .setTimeMax(org.mockito.ArgumentMatchers.any())
                .setOrderBy(anyString())
                .setSingleEvents(true)
                .execute()
        ).thenReturn(events);

        CalendarService service = new CalendarService("id", "secret", "token", () -> mockCalendar);
        List<CalendarService.CalendarEvent> result = service.getTodayEvents();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("공휴일");
        assertThat(result.get(0).startTime()).isEqualTo("종일");
        assertThat(result.get(0).allDay()).isTrue();
        assertThat(result.get(0).location()).isNull();
    }

    @Test
    void getTodayEvents_제목_없으면_기본값() throws Exception {
        Calendar mockCalendar = mock(Calendar.class, RETURNS_DEEP_STUBS);

        Events events = new Events().setItems(List.of(
                new Event()
                        .setStart(new EventDateTime().setDate(new DateTime("2026-03-12")))
        ));

        when(mockCalendar.events().list(anyString())
                .setTimeMin(org.mockito.ArgumentMatchers.any())
                .setTimeMax(org.mockito.ArgumentMatchers.any())
                .setOrderBy(anyString())
                .setSingleEvents(true)
                .execute()
        ).thenReturn(events);

        CalendarService service = new CalendarService("id", "secret", "token", () -> mockCalendar);
        List<CalendarService.CalendarEvent> result = service.getTodayEvents();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("(제목 없음)");
    }

    @Test
    void getTodayEvents_여러_이벤트() throws Exception {
        Calendar mockCalendar = mock(Calendar.class, RETURNS_DEEP_STUBS);

        long millis1 = LocalDate.now(CalendarService.KST).atTime(9, 0)
                .atZone(CalendarService.KST).toInstant().toEpochMilli();
        long millis2 = LocalDate.now(CalendarService.KST).atTime(15, 30)
                .atZone(CalendarService.KST).toInstant().toEpochMilli();

        Events events = new Events().setItems(List.of(
                new Event().setSummary("아침 스탠드업")
                        .setStart(new EventDateTime().setDateTime(new DateTime(millis1))),
                new Event().setSummary("종일 워크숍")
                        .setStart(new EventDateTime().setDate(new DateTime("2026-03-12"))),
                new Event().setSummary("오후 미팅")
                        .setLocation("3층")
                        .setStart(new EventDateTime().setDateTime(new DateTime(millis2)))
        ));

        when(mockCalendar.events().list(anyString())
                .setTimeMin(org.mockito.ArgumentMatchers.any())
                .setTimeMax(org.mockito.ArgumentMatchers.any())
                .setOrderBy(anyString())
                .setSingleEvents(true)
                .execute()
        ).thenReturn(events);

        CalendarService service = new CalendarService("id", "secret", "token", () -> mockCalendar);
        List<CalendarService.CalendarEvent> results = service.getTodayEvents();

        assertThat(results).hasSize(3);
        assertThat(results.get(0).title()).isEqualTo("아침 스탠드업");
        assertThat(results.get(0).startTime()).isEqualTo("09:00");
        assertThat(results.get(1).allDay()).isTrue();
        assertThat(results.get(2).location()).isEqualTo("3층");
    }

    @Test
    void getTodayEvents_일정_없는_경우() throws Exception {
        Calendar mockCalendar = mock(Calendar.class, RETURNS_DEEP_STUBS);

        Events events = new Events().setItems(List.of());

        when(mockCalendar.events().list(anyString())
                .setTimeMin(org.mockito.ArgumentMatchers.any())
                .setTimeMax(org.mockito.ArgumentMatchers.any())
                .setOrderBy(anyString())
                .setSingleEvents(true)
                .execute()
        ).thenReturn(events);

        CalendarService service = new CalendarService("id", "secret", "token", () -> mockCalendar);
        List<CalendarService.CalendarEvent> result = service.getTodayEvents();

        assertThat(result).isEmpty();
    }

    @Test
    void getTodayEvents_items_null인_경우() throws Exception {
        Calendar mockCalendar = mock(Calendar.class, RETURNS_DEEP_STUBS);

        Events events = new Events();

        when(mockCalendar.events().list(anyString())
                .setTimeMin(org.mockito.ArgumentMatchers.any())
                .setTimeMax(org.mockito.ArgumentMatchers.any())
                .setOrderBy(anyString())
                .setSingleEvents(true)
                .execute()
        ).thenReturn(events);

        CalendarService service = new CalendarService("id", "secret", "token", () -> mockCalendar);
        List<CalendarService.CalendarEvent> result = service.getTodayEvents();

        assertThat(result).isEmpty();
    }

    @Test
    void getTodayEvents_인증_실패시_예외_전파() {
        CalendarService service = new CalendarService("id", "secret", "token", () -> {
            throw new RuntimeException("Google Calendar 인증 실패 — refresh token 갱신 필요");
        });

        assertThatThrownBy(service::getTodayEvents)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("인증 실패");
    }

    @Test
    void getTodayEvents_API_호출_예외시_빈_리스트() throws Exception {
        Calendar mockCalendar = mock(Calendar.class, RETURNS_DEEP_STUBS);

        when(mockCalendar.events().list(anyString())
                .setTimeMin(org.mockito.ArgumentMatchers.any())
                .setTimeMax(org.mockito.ArgumentMatchers.any())
                .setOrderBy(anyString())
                .setSingleEvents(true)
                .execute()
        ).thenThrow(new RuntimeException("API 네트워크 오류"));

        CalendarService service = new CalendarService("id", "secret", "token", () -> mockCalendar);
        List<CalendarService.CalendarEvent> result = service.getTodayEvents();

        assertThat(result).isEmpty();
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
