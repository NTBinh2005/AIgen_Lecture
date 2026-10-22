package com.example.demo.service.serviceImpl;

import com.example.demo.common.exception.ApiException;
import com.example.demo.common.exception.BadRequestException;
import com.example.demo.common.exception.ErrorCode;
import com.example.demo.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.MimeMessageHelper;
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

    @Override
    public void sendEmail(String toEmail, String subject, String body) {
        if (!StringUtils.hasText(toEmail)) {
            throw new BadRequestException("Địa chỉ email người nhận không hợp lệ");
        }

        if (!StringUtils.hasText(mailUsername)) {
            log.warn("[Email Service] Cannot send email to {} because MAIL_USERNAME is not configured", toEmail);
            throw new ApiException(ErrorCode.EMAIL_NOT_CONFIGURED);
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
            throw new ApiException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }

    @Async
    @Override
    public void sendNotificationEmail(String toEmail, String subject, String body) {
        if (!StringUtils.hasText(toEmail)) {
            return;
        }

        if (!StringUtils.hasText(mailUsername)) {
            log.debug("[Email Service] Skipped notification email to {} because MAIL_USERNAME is not configured", toEmail);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailUsername);
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(body);

            mailSender.send(message);
            log.info("[Email Service] Successfully sent notification email to {}", toEmail);
        } catch (Exception ex) {
            log.error("[Email Service] Failed to send notification email to {}: {}", toEmail, ex.getMessage());
        }
    }

    @Async
    @Override
    public void sendEmailWithAttachment(
            String toEmail,
            String subject,
            String body,
            String attachmentName,
            byte[] attachment,
            String contentType) {
        if (!StringUtils.hasText(toEmail)) {
            log.warn("[Email Service] Skipped attachment email because recipient is empty");
            return;
        }
        if (attachment == null || attachment.length == 0) {
            log.warn("[Email Service] Skipped attachment email to {} because attachment is empty", toEmail);
            return;
        }
        if (!StringUtils.hasText(mailUsername)) {
            log.debug("[Email Service] Skipped attachment {} to {} because MAIL_USERNAME is not configured",
                    attachmentName, toEmail);
            return;
        }

        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, java.nio.charset.StandardCharsets.UTF_8.name());
            helper.setFrom(mailUsername);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(body, false);
            helper.addAttachment(
                    StringUtils.hasText(attachmentName) ? attachmentName : "attachment",
                    new ByteArrayResource(attachment),
                    StringUtils.hasText(contentType) ? contentType : "application/octet-stream");

            mailSender.send(message);
            log.info("[Email Service] Successfully sent attachment {} to {}", attachmentName, toEmail);
        } catch (Exception ex) {
            log.error("[Email Service] Failed to send attachment email to {}: {}", toEmail, ex.getMessage(), ex);
        }
    }
}
