package com.dailymail.today;

import com.dailymail.config.MailConfig;
import com.dailymail.core.MailContent;
import com.dailymail.core.MailModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TodayBriefMail implements MailModule {

    private final CalendarService calendarService;
    private final MailConfig mailConfig;

    @Override
    public String name() {
        return "today-brief";
    }

    @Override
    public boolean isEnabled() {
        var config = mailConfig.modules().get(name());
        return config != null && config.enabled();
    }

    @Override
    public MailContent generate() {
        List<CalendarService.CalendarEvent> events = calendarService.getTodayEvents();

        if (events.isEmpty()) {
            var config = mailConfig.modules().get(name());
            if (config != null && config.skipIfEmpty()) {
                log.info("오늘 일정이 없어 메일을 스킵합니다.");
                return null;
            }
        }

        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("MM/dd"));
        String subject = events.isEmpty()
                ? String.format("[Today] %s — 오늘 일정 없음", today)
                : String.format("[Today] %s — 오늘 일정 %d건", today, events.size());

        return new MailContent(subject, "today-brief", Map.of(
                "date", today,
                "events", events,
                "hasEvents", !events.isEmpty()
        ));
    }
}
