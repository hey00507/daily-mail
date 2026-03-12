package com.dailymail.today;

import com.dailymail.config.GoogleCalendarConfig;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@Slf4j
@Service
public class CalendarService {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final GoogleCalendarConfig config;
    private final Supplier<Calendar> calendarSupplier;

    public CalendarService(GoogleCalendarConfig config, Supplier<Calendar> calendarSupplier) {
        this.config = config;
        this.calendarSupplier = calendarSupplier;
    }

    public boolean isConfigured() {
        return config.isConfigured();
    }

    public List<CalendarEvent> getTodayEvents() {
        if (!isConfigured()) {
            log.warn("Google Calendar 인증 정보가 없습니다. GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET, GOOGLE_REFRESH_TOKEN을 설정해주세요.");
            return List.of();
        }

        Calendar service = calendarSupplier.get();

        try {
            LocalDate today = LocalDate.now(KST);
            DateTime timeMin = toDateTime(today.atStartOfDay());
            DateTime timeMax = toDateTime(today.plusDays(1).atStartOfDay());

            Events events = service.events().list("primary")
                    .setTimeMin(timeMin)
                    .setTimeMax(timeMax)
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .execute();

            List<Event> items = events.getItems();
            if (items == null || items.isEmpty()) {
                return List.of();
            }

            return toCalendarEvents(items);

        } catch (Exception e) {
            log.error("Google Calendar API 호출 실패", e);
            return List.of();
        }
    }

    private List<CalendarEvent> toCalendarEvents(List<Event> items) {
        List<CalendarEvent> result = new ArrayList<>();
        for (Event event : items) {
            result.add(toCalendarEvent(event));
        }
        return result;
    }

    private CalendarEvent toCalendarEvent(Event event) {
        boolean allDay = event.getStart().getDateTime() == null;
        String startTime;
        if (allDay) {
            startTime = "종일";
        } else {
            LocalDateTime ldt = LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(event.getStart().getDateTime().getValue()),
                    KST
            );
            startTime = ldt.format(DateTimeFormatter.ofPattern("HH:mm"));
        }

        return new CalendarEvent(
                event.getSummary() != null ? event.getSummary() : "(제목 없음)",
                startTime,
                allDay,
                event.getLocation(),
                event.getHtmlLink()
        );
    }

    private static DateTime toDateTime(LocalDateTime ldt) {
        long millis = ldt.atZone(KST).toInstant().toEpochMilli();
        return new DateTime(millis);
    }

    public record CalendarEvent(
            String title,
            String startTime,
            boolean allDay,
            String location,
            String link
    ) {}
}
