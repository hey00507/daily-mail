package com.dailymail.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HistoryServiceTest {

    private HistoryService historyService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        String path = tempDir.resolve("history.json").toString();
        historyService = new HistoryService(path);
    }

    @Test
    void 파일없으면_빈_리스트_반환() {
        List<Map<String, Object>> history = historyService.load();
        assertThat(history).isEmpty();
    }

    @Test
    void append_후_load로_조회() {
        historyService.append("cs-daily", "OS: 프로세스 vs 스레드");

        List<Map<String, Object>> history = historyService.load();
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().get("module")).isEqualTo("cs-daily");
        assertThat(history.getFirst().get("topic")).isEqualTo("OS: 프로세스 vs 스레드");
        assertThat(history.getFirst().get("date")).isNotNull();
    }

    @Test
    void getSentTopics_모듈별_필터링() {
        historyService.append("cs-daily", "OS: 프로세스 vs 스레드");
        historyService.append("cs-daily", "Network: TCP vs UDP");
        historyService.append("news-brief", "뉴스 기사 1");

        List<String> csTopics = historyService.getSentTopics("cs-daily");
        assertThat(csTopics).hasSize(2);
        assertThat(csTopics).containsExactly("OS: 프로세스 vs 스레드", "Network: TCP vs UDP");

        List<String> newsTopics = historyService.getSentTopics("news-brief");
        assertThat(newsTopics).hasSize(1);
        assertThat(newsTopics).containsExactly("뉴스 기사 1");
    }

    @Test
    void getSentTopics_없는_모듈은_빈_리스트() {
        historyService.append("cs-daily", "OS: 프로세스 vs 스레드");

        List<String> topics = historyService.getSentTopics("today-brief");
        assertThat(topics).isEmpty();
    }

    @Test
    void 여러번_append_누적() {
        historyService.append("cs-daily", "주제1");
        historyService.append("cs-daily", "주제2");
        historyService.append("cs-daily", "주제3");

        List<Map<String, Object>> history = historyService.load();
        assertThat(history).hasSize(3);
    }
}
