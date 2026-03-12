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
    private final DiscordService discordService;
    private final String targetModule;

    public MailRunner(
            List<MailModule> modules,
            MailService mailService,
            DiscordService discordService,
            @Value("${mail.module}") String targetModule
    ) {
        this.modules = modules;
        this.mailService = mailService;
        this.discordService = discordService;
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
                processModule(module);
            } catch (Exception e) {
                log.error("[{}] 처리 중 오류 발생", module.name(), e);
            }
        }

        log.info("Daily Mail 완료");
    }

    private void processModule(MailModule module) {
        log.info("[{}] 콘텐츠 생성 시작", module.name());
        MailContent content = module.generate();

        if (content == null) {
            log.info("[{}] 발송할 콘텐츠 없음 (스킵)", module.name());
            return;
        }

        mailService.send(content);
        sendToDiscord(module, content);
    }

    private void sendToDiscord(MailModule module, MailContent content) {
        try {
            discordService.send(content);
        } catch (DiscordSendException e) {
            log.error("[{}] Discord 발송 실패 (메일은 정상 발송): {}", module.name(), e.getMessage());
        }
    }
}
