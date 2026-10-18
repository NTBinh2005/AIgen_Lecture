package com.example.demo.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.demo.common.response.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/test");
    }

    @Test
    void replacesLegacyEnglishMessagesBeforeReturningThemToTheClient() {
        ResponseEntity<ApiError> response = handler.handleBadRequest(
                new BadRequestException("Student not found: 99999"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Dữ liệu yêu cầu không hợp lệ");
    }

    @Test
    void keepsDetailedVietnameseMessages() {
        ResponseEntity<ApiError> response = handler.handleConflict(
                new ConflictException("Bạn đã có lớp tên Toán 12"), request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Bạn đã có lớp tên Toán 12");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Student đã được điểm danh trong buổi này",
            "Lịch buổi học bị trùng với session #123",
            "Session đã bị hủy trước đó",
            "Bạn đã có lớp tên Lop Quiz 1",
            "Bạn đã có lớp tên User File Session Only",
            "File export đã hết hạn",
            "Parent comment không thuộc bài giảng này"
    })
    void keepsVietnameseMessagesContainingEnglishTermsOrUserData(String message) {
        ResponseEntity<ApiError> response = handler.handleBadRequest(
                new BadRequestException(message), request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo(message);
        assertThat(handler.handleConflict(new ConflictException(message), request).getBody().message())
                .isEqualTo(message);
    }

    @Test
    void apiExceptionKeepsItsStableCodeAndVietnameseMessage() {
        ResponseEntity<ApiError> response = handler.handleApiException(
                new ApiException(ErrorCode.QUIZ_NOT_FOUND), request);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("QUIZ_001");
        assertThat(response.getBody().message()).isEqualTo("Không tìm thấy bài kiểm tra");
    }
}
