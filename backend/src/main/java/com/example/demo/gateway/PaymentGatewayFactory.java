package com.example.demo.gateway;

import com.example.demo.common.exception.BadRequestException;
import com.example.demo.entity.PaymentMethod;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Factory tra cứu implementation {@link PaymentGateway} theo {@link PaymentMethod}.
 * <p>
 * Bean mapping: Spring tự động inject các gateway thông qua {@code @Component("VNPAY")}
 * và {@code @Component("MOMO")}.
 */
@Component
@RequiredArgsConstructor
public class PaymentGatewayFactory {

    private final Map<String, PaymentGateway> gateways;

    /**
     * Lấy gateway tương ứng với phương thức thanh toán.
     *
     * @param method phương thức thanh toán ({@link PaymentMethod#VNPAY} hoặc {@link PaymentMethod#MOMO})
     * @return cài đặt gateway tương ứng
     * @throws BadRequestException nếu phương thức không được hỗ trợ
     */
    public PaymentGateway getGateway(PaymentMethod method) {
        if (method == null) {
            throw new BadRequestException("Payment method không được để trống");
        }
        PaymentGateway gateway = gateways.get(method.name());
        if (gateway == null) {
            throw new BadRequestException("Payment method không được hỗ trợ: " + method);
        }
        return gateway;
    }
}
