package com.example.demo.service.serviceImpl;

import com.example.demo.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Async
    @Override
    public void sendEmail(String toEmail, String subject, String body) {
        if (!StringUtils.hasText(toEmail)) {
            return;
        }

        if (!StringUtils.hasText(mailUsername)) {
            log.info("[Email Service] No MAIL_USERNAME configured. Mock sending email to {}: [{}] {}",
                    toEmail, subject, body);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailUsername);
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(body);

            mailSender.send(message);
            log.info("[Email Service] Successfully sent email to {}", toEmail);
        } catch (Exception ex) {
            log.error("[Email Service] Failed to send email to {}: {}", toEmail, ex.getMessage());
        }
    }
}
