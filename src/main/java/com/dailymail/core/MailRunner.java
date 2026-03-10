package com.dailymail.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class MailRunner implements CommandLineRunner {

    private final List<MailModule> modules;
    private final MailService mailService;
    private final String targetModule;

    public MailRunner(
            List<MailModule> modules,
            MailService mailService,
            @Value("${mail.module}") String targetModule
    ) {
        this.modules = modules;
        this.mailService = mailService;
        this.targetModule = targetModule;
    }

    @Override
    public void run(String... args) {
        log.info("Daily Mail 실행 — target: {}", targetModule);

        List<MailModule> targets = modules.stream()
                .filter(MailModule::isEnabled)
                .filter(m -> "all".equals(targetModule) || m.name().equals(targetModule))
                .toList();

        if (targets.isEmpty()) {
            log.warn("실행할 모듈이 없습니다: {}", targetModule);
            return;
        }

        for (MailModule module : targets) {
            try {
                log.info("[{}] 콘텐츠 생성 시작", module.name());
                MailContent content = module.generate();
                if (content != null) {
                    mailService.send(content);
                } else {
                    log.info("[{}] 발송할 콘텐츠 없음 (스킵)", module.name());
                }
            } catch (Exception e) {
                log.error("[{}] 처리 중 오류 발생", module.name(), e);
            }
        }

        log.info("Daily Mail 완료");
    }
}
