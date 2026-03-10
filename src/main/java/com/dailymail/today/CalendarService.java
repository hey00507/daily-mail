package com.dailymail.today;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class CalendarService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final List<String> SCOPES = List.of("https://www.googleapis.com/auth/calendar.readonly");

    private final String credentialsBase64;

    public CalendarService(@Value("${google.calendar.credentials:}") String credentialsBase64) {
        this.credentialsBase64 = credentialsBase64;
    }

    public List<CalendarEvent> getTodayEvents() {
        if (credentialsBase64 == null || credentialsBase64.isBlank()) {
            log.warn("Google Calendar 인증 정보가 없습니다. GOOGLE_CREDENTIALS를 설정해주세요.");
            return List.of();
        }

        try {
            Calendar service = buildCalendarService();

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

            List<CalendarEvent> result = new ArrayList<>();
            for (Event event : items) {
                result.add(toCalendarEvent(event));
            }
            return result;

        } catch (Exception e) {
            log.error("Google Calendar API 호출 실패", e);
            return List.of();
        }
    }

    private Calendar buildCalendarService() throws Exception {
        byte[] credBytes = Base64.getDecoder().decode(credentialsBase64);
        GoogleCredentials credentials = GoogleCredentials
                .fromStream(new ByteArrayInputStream(credBytes))
                .createScoped(SCOPES);

        return new Calendar.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName("daily-mail")
                .build();
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

    private DateTime toDateTime(LocalDateTime ldt) {
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
