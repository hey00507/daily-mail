package com.dailymail.today;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.UserCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class CalendarService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final List<String> SCOPES = List.of("https://www.googleapis.com/auth/calendar.readonly");

    private final String clientId;
    private final String clientSecret;
    private final String refreshToken;

    public CalendarService(
            @Value("${google.calendar.client-id:}") String clientId,
            @Value("${google.calendar.client-secret:}") String clientSecret,
            @Value("${google.calendar.refresh-token:}") String refreshToken
    ) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.refreshToken = refreshToken;
    }

    public List<CalendarEvent> getTodayEvents() {
        if (clientId.isBlank() || clientSecret.isBlank() || refreshToken.isBlank()) {
            log.warn("Google Calendar 인증 정보가 없습니다. GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET, GOOGLE_REFRESH_TOKEN을 설정해주세요.");
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
        GoogleCredentials credentials = UserCredentials.newBuilder()
                .setClientId(clientId)
                .setClientSecret(clientSecret)
                .setRefreshToken(refreshToken)
                .build();
        credentials.refreshIfExpired();

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
