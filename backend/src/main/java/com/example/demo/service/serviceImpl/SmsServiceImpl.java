package com.example.demo.service.serviceImpl;

import com.example.demo.service.SmsService;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
public class SmsServiceImpl implements SmsService {
    private final RestClient restClient = RestClient.create();
    private final String accountSid;
    private final String authToken;
    private final String fromNumber;

    public SmsServiceImpl(
            @Value("${twilio.account-sid:}") String accountSid,
            @Value("${twilio.auth-token:}") String authToken,
            @Value("${twilio.from-number:}") String fromNumber) {
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.fromNumber = fromNumber;
    }

    @Override
    public void sendOtp(String phoneNumber, String otp) {
        if (!StringUtils.hasText(accountSid) || !StringUtils.hasText(authToken) || !StringUtils.hasText(fromNumber)) {
            log.info("SMS OTP for {} is {}. Configure Twilio properties to send real SMS.", phoneNumber, otp);
            return;
        }

        LinkedMultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("To", phoneNumber);
        body.add("From", fromNumber);
        body.add("Body", "Your AIGen Lecture verification code is: " + otp);

        restClient.post()
                .uri("https://api.twilio.com/2010-04-01/Accounts/{accountSid}/Messages.json",
                        Map.of("accountSid", accountSid))
                .headers(headers -> headers.setBasicAuth(accountSid, authToken, StandardCharsets.UTF_8))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }
}
