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
    ADMIN_REQUIRED(HttpStatus.FORBIDDEN, "ADM_001", "Admin access required"),
    ADMIN_ID_REQUIRED(HttpStatus.BAD_REQUEST, "ADM_002", "AdminId không được để trống"),

    // =========================================================================
    // QUIZ & ASSIGNMENT
    // =========================================================================
    QUIZ_NOT_FOUND(HttpStatus.NOT_FOUND, "QUIZ_001", "Quiz not found"),
    QUIZ_VERSION_IMMUTABLE(HttpStatus.BAD_REQUEST, "QUIZ_002", "Quiz version is immutable and cannot be modified"),
    QUIZ_PUBLISH_VALIDATION(HttpStatus.BAD_REQUEST, "QUIZ_003", "Quiz fails publish validation"),
    TEMPLATE_INVALID(HttpStatus.BAD_REQUEST, "QUIZ_004", "Excel template is invalid"),
    ASSIGNMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "ASSIGN_001", "Quiz assignment not found"),
    ATTEMPT_NOT_FOUND(HttpStatus.NOT_FOUND, "ATTEMPT_001", "Attempt not found"),
    ATTEMPT_SUBMITTED(HttpStatus.BAD_REQUEST, "ATTEMPT_002", "Attempt already submitted"),
    ATTEMPT_DEADLINE_PASSED(HttpStatus.BAD_REQUEST, "ATTEMPT_003", "Attempt deadline has passed");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
