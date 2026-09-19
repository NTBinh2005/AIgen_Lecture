package com.example.demo.common.response;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;

    private String message;

    private T data;

    private ApiError error;

    private PaginationMeta pagination;

    private Instant timestamp;

    public static <T> ApiResponse<T> success(
            String message,
            T data
    ) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(Instant.now())
                .build();
    }

    public static <T> ApiResponse<T> success(String message, T data, PaginationMeta pagination) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .pagination(pagination)
                .timestamp(Instant.now())
                .build();
    }

    public static <T> ApiResponse<List<T>> successPage(String message, Page<T> page) {
        PaginationMeta pagination = PaginationMeta.builder()
                .pageNum(page.getNumber())
                .pageSize(page.getSize())
                .totalItems(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();

        return ApiResponse.<List<T>>builder()
                .success(true)
                .message(message)
                .data(page.getContent())
                .pagination(pagination)
                .timestamp(Instant.now())
                .build();
    }

    public static <T> ApiResponse<T> fail(String message, ApiError error) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .error(error)
                .timestamp(Instant.now())
                .build();
    }
}