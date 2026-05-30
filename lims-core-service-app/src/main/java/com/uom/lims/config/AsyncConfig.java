package com.uom.lims.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Bounded executors for asynchronous work. Without an explicit executor, Spring
 * falls back to {@code SimpleAsyncTaskExecutor}, which spawns an unbounded thread
 * per task — unsafe under load. The notification executor backs the AFTER_COMMIT
 * email/SMS dispatch so a slow provider never pins a request or DB connection.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("notify-");
        // Run on the caller if the queue is saturated rather than dropping a
        // patient notification.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
