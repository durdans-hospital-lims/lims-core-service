package com.uom.lims.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReportDispatchKafkaEventListener {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String TOPIC = "report.dispatch.events";

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleDispatchEvent(ReportDispatchDomainEvent event) {
        log.info("Publishing dispatch Kafka event AFTER COMMIT: {}", event);
        kafkaTemplate.send(TOPIC, event.reportReference(), event)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("Dispatch Kafka event sent: {}", event.eventType());
                    } else {
                        log.error("Dispatch Kafka event send failed", ex);
                    }
                });
    }
}
