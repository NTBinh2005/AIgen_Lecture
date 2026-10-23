package com.example.demo.common.exception;

import com.example.demo.common.response.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final Pattern VIETNAMESE_TEXT = Pattern.compile(".*[À-ỹĐđ].*", Pattern.DOTALL);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handleApiException(ApiException ex, HttpServletRequest request) {
        ErrorCode errorCode = ex.getErrorCode();
        return build(
                errorCode.getStatus(),
                errorCode.getCode(),
                clientMessage(ex.getMessage(), defaultMessage(errorCode.getStatus())),
                request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND,
                clientMessage(ex.getMessage(), "Không tìm thấy tài nguyên được yêu cầu"), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST,
                clientMessage(ex.getMessage(), "Dữ liệu yêu cầu không hợp lệ"), request);
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiError> handleIllegalState(IllegalStateException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT,
                clientMessage(ex.getMessage(), "Không thể thực hiện thao tác ở trạng thái hiện tại"), request);
    }

    @ExceptionHandler(org.springframework.web.multipart.support.MissingServletRequestPartException.class)
    ResponseEntity<ApiError> handleMissingPart(org.springframework.web.multipart.support.MissingServletRequestPartException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Thiếu dữ liệu upload: " + ex.getRequestPartName(), request);
    }

    @ExceptionHandler(BadRequestException.class)
    ResponseEntity<ApiError> handleBadRequest(BadRequestException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST,
                clientMessage(ex.getMessage(), "Dữ liệu yêu cầu không hợp lệ"), request);
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ApiError> handleConflict(ConflictException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT,
                clientMessage(ex.getMessage(), "Dữ liệu bị xung đột với trạng thái hiện tại"), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": "
                        + clientMessage(error.getDefaultMessage(), "Giá trị không hợp lệ"))
                .collect(Collectors.joining("; "));
        if (message.isBlank()) {
            message = "Dữ liệu đầu vào không hợp lệ";
        }
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Dữ liệu yêu cầu không hợp lệ", request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiError> handleMissingParam(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST,
                "Thiếu tham số bắt buộc: '" + ex.getParameterName() + "'", request);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ApiError> handleMissingHeader(
            MissingRequestHeaderException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST,
                "Thiếu header bắt buộc: '" + ex.getHeaderName() + "'", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST,
                "Giá trị không hợp lệ cho tham số '" + ex.getName() + "'", request);
    }

    @ExceptionHandler(PropertyReferenceException.class)
    ResponseEntity<ApiError> handleInvalidSort(
            PropertyReferenceException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST,
                "Trường sắp xếp không hợp lệ: '" + ex.getPropertyName() + "'", request);
    }

    @ExceptionHandler(InvalidDataAccessApiUsageException.class)
    ResponseEntity<ApiError> handleInvalidDataAccessUsage(
            InvalidDataAccessApiUsageException ex, HttpServletRequest request) {
        if (request.getParameterValues("sort") != null && hasInvalidSortCause(ex)) {
            return build(HttpStatus.BAD_REQUEST, "Trường sắp xếp không hợp lệ", request);
        }
        log.error("Invalid data access usage on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "Lỗi hệ thống. Vui lòng thử lại sau.", request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> handleMaxUploadSize(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "Tệp quá lớn. Giới hạn tối đa là 20 MB.", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.CONFLICT, "Dữ liệu vi phạm ràng buộc hoặc bị trùng", request);
    }

    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ApiError> handleOptimisticLocking(org.springframework.orm.ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "Dữ liệu đã bị thay đổi bởi luồng khác. Vui lòng tải lại.", request);
    }

    @ExceptionHandler(org.springframework.security.authentication.BadCredentialsException.class)
    ResponseEntity<ApiError> handleBadCredentials(
            org.springframework.security.authentication.BadCredentialsException ex,
            HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "Thông tin đăng nhập không chính xác", request);
    }

    @ExceptionHandler(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class)
    ResponseEntity<ApiError> handleMissingCredentials(
            org.springframework.security.authentication.AuthenticationCredentialsNotFoundException ex,
            HttpServletRequest request) {
        // Controller yêu cầu đăng nhập nhưng không có principal → 401 thay vì 500 (FIX #9).
        return build(HttpStatus.UNAUTHORIZED, "Yêu cầu đăng nhập", request);
    }

    @ExceptionHandler(SubscriptionRequiredException.class)
    ResponseEntity<ApiError> handleSubscriptionRequired(
            SubscriptionRequiredException ex,
            HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "SUBSCRIPTION_REQUIRED",
                clientMessage(ex.getMessage(), "Bạn cần gói đăng ký còn hiệu lực để sử dụng tính năng này"), request);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    ResponseEntity<ApiError> handleAccessDenied(
            org.springframework.security.access.AccessDeniedException ex,
            HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện hành động này", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleAll(Exception ex, HttpServletRequest request) {
        if (ex instanceof RuntimeException && ex.getMessage() != null && ex.getMessage().contains("timeout")) {
            return build(HttpStatus.SERVICE_UNAVAILABLE,
                    "Dịch vụ tạm thời quá tải, vui lòng thử lại sau", request);
        }
        // Log chi tiết phía server, nhưng không trả thông tin nội bộ về cho client.
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "Lỗi hệ thống. Vui lòng thử lại sau.", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "Không tìm thấy endpoint", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "Phương thức HTTP không được hỗ trợ cho endpoint này", request);
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<ApiError> handleNoSuchElement(NoSuchElementException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "Không tìm thấy tài nguyên được yêu cầu", request);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest request) {
        return build(status, null, message, request);
    }

    private ResponseEntity<ApiError> build(
            HttpStatus status, String code, String message, HttpServletRequest request) {
        ApiError error = new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                request.getRequestURI()
        );
        return ResponseEntity.status(status).body(error);
    }

    /**
     * Không chuyển thẳng thông báo tiếng Anh từ thư viện hoặc các nhánh nghiệp vụ cũ ra API.
     * Thông báo chi tiết vẫn được giữ nếu đã là tiếng Việt; chi tiết kỹ thuật đầy đủ chỉ nằm trong log.
     */
    private String clientMessage(String message, String fallback) {
        if (message == null || message.isBlank()) {
            return fallback;
        }
        boolean isVietnamese = VIETNAMESE_TEXT.matcher(message).matches();
        return isVietnamese ? message : fallback;
    }

    private String defaultMessage(HttpStatus status) {
        if (status == HttpStatus.NOT_FOUND) {
            return "Không tìm thấy tài nguyên được yêu cầu";
        }
        if (status == HttpStatus.UNAUTHORIZED) {
            return "Yêu cầu đăng nhập";
        }
        if (status == HttpStatus.FORBIDDEN) {
            return "Bạn không có quyền thực hiện hành động này";
        }
        if (status == HttpStatus.CONFLICT) {
            return "Dữ liệu bị xung đột với trạng thái hiện tại";
        }
        if (status == HttpStatus.SERVICE_UNAVAILABLE) {
            return "Dịch vụ tạm thời không khả dụng, vui lòng thử lại sau";
        }
        return "Dữ liệu yêu cầu không hợp lệ";
    }

    private boolean hasInvalidSortCause(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof PropertyReferenceException) {
                return true;
            }
            String type = current.getClass().getName();
            if (type.equals("org.hibernate.query.sqm.UnknownPathException")
                    || type.equals("org.hibernate.query.sqm.PathElementException")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
