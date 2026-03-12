package com.dailymail.core;

import com.dailymail.config.MailConfig;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MailServiceTest {

    @Mock
    JavaMailSender mailSender;

    @Mock
    TemplateEngine templateEngine;

    @Mock
    MailConfig mailConfig;

    private MailService createMailService() {
        return new MailService(mailSender, templateEngine, mailConfig, "test-sender@gmail.com");
    }

    @Test
    void send_정상발송() {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(mailConfig.recipient()).thenReturn("test@gmail.com");
        when(templateEngine.process(eq("cs-daily"), any(Context.class)))
                .thenReturn("<html>테스트</html>");

        var mailService = createMailService();
        var content = new MailContent("[CS] 테스트", "cs-daily", Map.of("key", "value"));
        mailService.send(content);

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void send_템플릿_변수_전달() {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(mailConfig.recipient()).thenReturn("test@gmail.com");

        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        when(templateEngine.process(eq("cs-daily"), contextCaptor.capture()))
                .thenReturn("<html></html>");

        var mailService = createMailService();
        var content = new MailContent("[CS] 테스트", "cs-daily",
                Map.of("category", "OS", "topic", "데드락"));
        mailService.send(content);

        Context captured = contextCaptor.getValue();
        assertThat(captured.getVariable("category")).isEqualTo("OS");
        assertThat(captured.getVariable("topic")).isEqualTo("데드락");
    }

    @Test
    void send_MessagingException_발생시_RuntimeException() {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(mailConfig.recipient()).thenReturn("test@gmail.com");
        when(templateEngine.process(eq("cs-daily"), any(Context.class)))
                .thenReturn("<html></html>");
        doThrow(new org.springframework.mail.MailSendException("SMTP 에러"))
                .when(mailSender).send(any(MimeMessage.class));

        var mailService = createMailService();
        var content = new MailContent("[CS] 테스트", "cs-daily", Map.of());

        assertThatThrownBy(() -> mailService.send(content))
                .isInstanceOf(Exception.class);
    }

    @Test
    void send_올바른_템플릿_이름_사용() {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(mailConfig.recipient()).thenReturn("test@gmail.com");
        when(templateEngine.process(eq("news-brief"), any(Context.class)))
                .thenReturn("<html></html>");

        var mailService = createMailService();
        var content = new MailContent("[News] 테스트", "news-brief", Map.of());
        mailService.send(content);

        verify(templateEngine).process(eq("news-brief"), any(Context.class));
    }
}
