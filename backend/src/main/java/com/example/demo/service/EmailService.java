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

    /**
     * Gửi email kèm một tệp đính kèm được giữ hoàn toàn trong bộ nhớ.
     *
     * @param toEmail        địa chỉ email người nhận
     * @param subject        tiêu đề email
     * @param body           nội dung email dạng text
     * @param attachmentName tên tệp hiển thị cho người nhận
     * @param attachment     nội dung tệp
     * @param contentType    MIME type của tệp
     */
    void sendEmailWithAttachment(
            String toEmail,
            String subject,
            String body,
            String attachmentName,
            byte[] attachment,
            String contentType);
}
