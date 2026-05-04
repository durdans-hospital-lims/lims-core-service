package com.uom.lims.event;

import com.uom.lims.dispatch.DispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClinicalReportAuthorizedDispatchListener {

    private final DispatchService dispatchService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleClinicalAuthorization(ClinicalReportAuthorizedEvent event) {
        try {
            dispatchService.registerAuthorizedReportSystem(event.request(), event.auditSource());
        } catch (Exception exception) {
            log.error("Failed to register dispatch item after clinical authorization for report {}",
                    event.request().getReportReference(), exception);
        }
    }
}
