package com.globalrfq.rfq.intake.repository;

import com.globalrfq.rfq.intake.entity.RfqImportPreview;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RfqImportPreviewRepository extends JpaRepository<RfqImportPreview, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from RfqImportPreview p where p.previewToken = :token")
    Optional<RfqImportPreview> findByTokenForUpdate(@Param("token") String token);

    List<RfqImportPreview> findTop100ByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(RfqImportPreview.Status status, Instant now);
    List<RfqImportPreview> findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(RfqImportPreview.Status status, Instant olderThan);
}
