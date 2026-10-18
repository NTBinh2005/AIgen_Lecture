package com.example.demo.config;

import com.example.demo.common.exception.BadRequestException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring Data silently changes a negative page number to zero and caps an
 * oversized page. Reject invalid client input instead so every paginated API
 * follows the same contract.
 */
@Configuration
public class PaginationValidationConfig implements WebMvcConfigurer {

    private static final int MAX_PAGE_SIZE = 100;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(
                    HttpServletRequest request,
                    HttpServletResponse response,
                    Object handler) {
                String pageValue = request.getParameter("page");
                String sizeValue = request.getParameter("size");
                if (pageValue != null && parseInteger(pageValue, "page") < 0) {
                    throw invalidPagination();
                }
                if (sizeValue != null) {
                    int size = parseInteger(sizeValue, "size");
                    if (size < 1 || size > MAX_PAGE_SIZE) {
                        throw invalidPagination();
                    }
                }
                return true;
            }
        });
    }

    private static int parseInteger(String value, String parameter) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new BadRequestException(
                    "Tham số '" + parameter + "' phải là số nguyên");
        }
    }

    private static BadRequestException invalidPagination() {
        return new BadRequestException("page phải >= 0, size từ 1 đến 100");
    }
}
