package com.uom.lims;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableCaching
@EnableJpaAuditing
@EnableAsync
@SpringBootApplication
public class LimsApplication {
    public static void main(String[] args) {
        SpringApplication.run(LimsApplication.class, args);
    }
}
