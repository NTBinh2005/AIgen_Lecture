package com.example.demo.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // =========================================================================
    // AUTH & MAIL
    // =========================================================================
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "AU_001", "Yêu cầu đăng nhập"),
    EMAIL_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "MAIL_001", "Hệ thống chưa cấu hình gửi email"),
    EMAIL_SEND_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "MAIL_002", "Không thể gửi email xác nhận, vui lòng thử lại sau"),

    // =========================================================================
    // USER
    // =========================================================================
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_001", "Không tìm thấy người dùng"),
    USER_EXISTED(HttpStatus.BAD_REQUEST, "USER_002", "Người dùng đã tồn tại"),
    USER_BANNED(HttpStatus.BAD_REQUEST, "USER_003", "Tài khoản người dùng đã bị khóa"),

    // =========================================================================
    // ADMIN
    // =========================================================================
    ADMIN_NOT_FOUND(HttpStatus.NOT_FOUND, "ADMIN_001", "Không tìm thấy quản trị viên"),
    ADMIN_REQUIRED(HttpStatus.FORBIDDEN, "ADM_001", "Yêu cầu quyền quản trị viên"),
    ADMIN_ID_REQUIRED(HttpStatus.BAD_REQUEST, "ADM_002", "AdminId không được để trống"),

    // =========================================================================
    // QUIZ & ASSIGNMENT
    // =========================================================================
    QUIZ_NOT_FOUND(HttpStatus.NOT_FOUND, "QUIZ_001", "Không tìm thấy bài kiểm tra"),
    QUIZ_VERSION_IMMUTABLE(HttpStatus.BAD_REQUEST, "QUIZ_002", "Không thể sửa phiên bản bài kiểm tra đã chốt"),
    QUIZ_PUBLISH_VALIDATION(HttpStatus.BAD_REQUEST, "QUIZ_003", "Bài kiểm tra chưa đủ điều kiện xuất bản"),
    TEMPLATE_INVALID(HttpStatus.BAD_REQUEST, "QUIZ_004", "Mẫu Excel không hợp lệ"),
    ASSIGNMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "ASSIGN_001", "Không tìm thấy bài kiểm tra đã giao"),
    ATTEMPT_NOT_FOUND(HttpStatus.NOT_FOUND, "ATTEMPT_001", "Không tìm thấy lượt làm bài"),
    ATTEMPT_SUBMITTED(HttpStatus.BAD_REQUEST, "ATTEMPT_002", "Lượt làm bài đã được nộp"),
    ATTEMPT_DEADLINE_PASSED(HttpStatus.BAD_REQUEST, "ATTEMPT_003", "Đã quá hạn nộp bài");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
