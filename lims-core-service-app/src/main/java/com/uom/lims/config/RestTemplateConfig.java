package com.uom.lims.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {
    // WHY: RestTemplate is used for internal service calls to patient API
    // to enrich order and bill responses with patient demographics
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
