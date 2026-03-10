package com.dailymail.core;

import com.dailymail.config.MailConfig;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final MailConfig mailConfig;

    public void send(MailContent content) {
        try {
            Context context = new Context();
            content.variables().forEach(context::setVariable);
            String html = templateEngine.process(content.template(), context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(mailConfig.recipient());
            helper.setSubject(content.subject());
            helper.setText(html, true);

            mailSender.send(message);
            log.info("메일 발송 완료: {}", content.subject());
        } catch (MessagingException e) {
            throw new RuntimeException("메일 발송 실패: " + content.subject(), e);
        }
    }
}
