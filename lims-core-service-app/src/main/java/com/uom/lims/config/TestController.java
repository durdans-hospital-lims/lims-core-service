package com.uom.lims.config;

import com.uom.lims.notification.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/test")
public class TestController {

    private final EmailService emailService;

    @GetMapping("/api/test")
    public String test(@AuthenticationPrincipal Jwt jwt) {
        return "Logged in as: " + jwt.getClaimAsString("preferred_username");
    }

    @GetMapping("/email")
    public String testEmail() {
        emailService.sendVerificationEmail("malinirmarjoki@gmail.com", "Test User", "TEST123");
        return "Email sent";
    }

}
