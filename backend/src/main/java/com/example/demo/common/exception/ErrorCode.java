package com.example.demo.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // =========================================================================
    // AUTH
    // =========================================================================
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "AU_001", "Unauthenticated"),

    // =========================================================================
    // USER
    // =========================================================================
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_001", "User not found"),
    USER_EXISTED(HttpStatus.BAD_REQUEST, "USER_002", "User already existed"),
    USER_BANNED(HttpStatus.BAD_REQUEST, "USER_003", "User is banned"),

    // =========================================================================
    // ADMIN
    // =========================================================================
    ADMIN_NOT_FOUND(HttpStatus.NOT_FOUND, "ADMIN_001", "ADMIN not found"),
    ADMIN_REQUIRED(HttpStatus.FORBIDDEN, "ADM_001", "Chỉ admin mới được tạo món ăn chuẩn"),
    ADMIN_ID_REQUIRED(HttpStatus.BAD_REQUEST, "ADM_002", "AdminId không được để trống");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
