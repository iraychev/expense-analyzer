package com.iraychev.expenseanalyzer.categorization;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Wakes the worker once the sync transaction has committed, so it sees the new rows. */
@Component
public class CategorizationTrigger {

    private final CategorizationWorker worker;

    public CategorizationTrigger(CategorizationWorker worker) {
        this.worker = worker;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSynced(TransactionsSyncedEvent event) {
        worker.triggerAsync();
    }
}
