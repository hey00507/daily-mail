package com.dailymail.today;

import com.dailymail.config.GoogleCalendarConfig;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.UserCredentials;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
public class GoogleCalendarClientFactory implements Supplier<Calendar> {

    private final GoogleCalendarConfig config;

    public GoogleCalendarClientFactory(GoogleCalendarConfig config) {
        this.config = config;
    }

    @Override
    public Calendar get() {
        try {
            GoogleCredentials credentials = UserCredentials.newBuilder()
                    .setClientId(config.clientId())
                    .setClientSecret(config.clientSecret())
                    .setRefreshToken(config.refreshToken())
                    .build();
            credentials.refreshIfExpired();

            return new Calendar.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credentials))
                    .setApplicationName("daily-mail")
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Google Calendar 클라이언트 생성 실패", e);
        }
    }
}
