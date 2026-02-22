package com.uom.lims.notification;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class MockSmsService implements SmsService {

    @Override
    public void sendSms(String phoneNumber, String message) {
        log.info("=== MOCK SMS ===");
        log.info("To: {}", phoneNumber);
        log.info("Message: {}", message);
        log.info("================");
    }
}
