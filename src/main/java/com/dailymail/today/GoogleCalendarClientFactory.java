package com.dailymail.today;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.UserCredentials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
public class GoogleCalendarClientFactory implements Supplier<Calendar> {

    private final String clientId;
    private final String clientSecret;
    private final String refreshToken;

    public GoogleCalendarClientFactory(
            @Value("${google.calendar.client-id:}") String clientId,
            @Value("${google.calendar.client-secret:}") String clientSecret,
            @Value("${google.calendar.refresh-token:}") String refreshToken
    ) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.refreshToken = refreshToken;
    }

    @Override
    public Calendar get() {
        try {
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
        } catch (Exception e) {
            throw new RuntimeException("Google Calendar 클라이언트 생성 실패", e);
        }
    }
}
