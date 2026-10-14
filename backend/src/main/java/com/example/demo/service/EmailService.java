package com.example.demo.service;

public interface EmailService {

    /**
     * Gửi email thông báo tới người nhận.
     *
     * @param toEmail địa chỉ email người nhận
     * @param subject tiêu đề email
     * @param body    nội dung email
     */
    void sendEmail(String toEmail, String subject, String body);
}
