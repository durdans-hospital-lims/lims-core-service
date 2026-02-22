package com.uom.lims.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

@Slf4j
@Component
@RequiredArgsConstructor
public class PatientKafkaEventListener {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String TOPIC = "patient.events";

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePatientEvent(PatientDomainEvent event) {

        log.info("Publishing Kafka event AFTER COMMIT: {}", event);

        kafkaTemplate.send(TOPIC, event.patientCode(), event)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("Kafka event sent successfully: {}", event.eventType());
                    } else {
                        log.error("Kafka event send failed", ex);
                    }
                });
    }
}
