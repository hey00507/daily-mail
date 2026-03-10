package com.dailymail.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class HistoryService {

    private final Path historyPath;
    private final ObjectMapper mapper;

    public HistoryService(@Value("${mail.history-path:data/history.json}") String historyPath) {
        this.historyPath = Path.of(historyPath);
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    public List<Map<String, Object>> load() {
        if (!Files.exists(historyPath)) {
            return new ArrayList<>();
        }
        try {
            return mapper.readValue(historyPath.toFile(), new TypeReference<>() {});
        } catch (IOException e) {
            log.warn("history.json 읽기 실패, 새로 시작합니다", e);
            return new ArrayList<>();
        }
    }

    public void append(String module, String topic) {
        List<Map<String, Object>> history = load();
        history.add(Map.of(
                "date", LocalDate.now().toString(),
                "module", module,
                "topic", topic
        ));
        try {
            Files.createDirectories(historyPath.getParent());
            mapper.writeValue(historyPath.toFile(), history);
            log.info("발송 이력 저장: {} - {}", module, topic);
        } catch (IOException e) {
            log.error("history.json 저장 실패", e);
        }
    }

    public List<String> getSentTopics(String module) {
        return load().stream()
                .filter(entry -> module.equals(entry.get("module")))
                .map(entry -> (String) entry.get("topic"))
                .toList();
    }
}
