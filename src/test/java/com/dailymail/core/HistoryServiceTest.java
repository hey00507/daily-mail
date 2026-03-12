package com.dailymail.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
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

    @Test
    void 잘못된_JSON_파일이면_빈_리스트_반환() throws IOException {
        Path historyPath = tempDir.resolve("bad-history.json");
        Files.writeString(historyPath, "이건 JSON이 아닙니다 {{{");

        HistoryService badService = new HistoryService(historyPath.toString());
        List<Map<String, Object>> history = badService.load();

        assertThat(history).isEmpty();
    }

    @Test
    void append_디렉토리_자동_생성() {
        Path nestedPath = tempDir.resolve("sub/dir/history.json");
        HistoryService nestedService = new HistoryService(nestedPath.toString());

        nestedService.append("cs-daily", "테스트 주제");

        List<Map<String, Object>> history = nestedService.load();
        assertThat(history).hasSize(1);
        assertThat(Files.exists(nestedPath)).isTrue();
    }

    @Test
    void append_후_파일_내용_검증() throws IOException {
        historyService.append("cs-daily", "OS: 프로세스 vs 스레드");

        Path historyPath = tempDir.resolve("history.json");
        String content = Files.readString(historyPath);

        assertThat(content).contains("cs-daily");
        assertThat(content).contains("OS: 프로세스 vs 스레드");
        assertThat(content).contains("date");
    }

    @Test
    void append_쓰기_실패해도_예외_전파_안함() throws IOException {
        // 파일 위치에 디렉토리를 만들어서 쓰기 실패 유발
        Path blockingFile = tempDir.resolve("blocked.json");
        Files.createDirectories(blockingFile);

        HistoryService blockedService = new HistoryService(blockingFile.toString());

        // IOException이 발생하지만 catch되어 예외가 전파되지 않음
        blockedService.append("cs-daily", "실패 테스트");
    }
}
