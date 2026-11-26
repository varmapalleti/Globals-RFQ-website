package com.globalrfq.rfq.intake.service;

import com.globalrfq.rfq.intake.entity.RfqImportPreview;
import com.globalrfq.rfq.intake.repository.RfqImportPreviewRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Component
public class RfqImportPreviewCleanup {
    private final RfqImportPreviewRepository previews;
    private final RfqImportPreviewStorage storage;

    public RfqImportPreviewCleanup(RfqImportPreviewRepository previews, RfqImportPreviewStorage storage) {
        this.previews = previews;
        this.storage = storage;
    }

    @Scheduled(fixedDelayString = "${RFQ_IMPORT_PREVIEW_CLEANUP_DELAY_MS:900000}")
    @Transactional
    public void cleanup() {
        Instant now = Instant.now();
        List<String> removeAfterCommit = new ArrayList<>();
        List<RfqImportPreview> expired = previews.findTop100ByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
                RfqImportPreview.Status.READY, now);
        for (RfqImportPreview preview : expired) {
            preview.expire();
            removeAfterCommit.add(preview.getStoredObjectKey());
        }
        previews.saveAll(expired);

        List<RfqImportPreview> consumed = previews.findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                RfqImportPreview.Status.CONSUMED, now.minus(1, ChronoUnit.DAYS));
        consumed.forEach(preview -> removeAfterCommit.add(preview.getStoredObjectKey()));
        if (!removeAfterCommit.isEmpty()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { removeAfterCommit.forEach(storage::delete); }
            });
        }
    }
}
