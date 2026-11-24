package com.globalrfq.rfq.intake.repository;

import com.globalrfq.rfq.intake.entity.ReviewerReassignmentRequest;
import com.globalrfq.rfq.intake.entity.ReviewerReassignmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewerReassignmentRequestRepository extends JpaRepository<ReviewerReassignmentRequest, Long> {
    boolean existsByRfqIdAndStatus(Long rfqId, ReviewerReassignmentStatus status);
    boolean existsByRfqIdAndRequestedByReviewerId(Long rfqId, Long reviewerId);
    Optional<ReviewerReassignmentRequest> findByRfqIdAndIdempotencyKey(Long rfqId, String idempotencyKey);

    List<ReviewerReassignmentRequest> findByRfqIdOrderByRequestedAtAsc(Long rfqId);

    List<ReviewerReassignmentRequest> findByRfqIdAndRequestedByReviewerIdOrderByRequestedAtAsc(Long rfqId, Long reviewerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from ReviewerReassignmentRequest request where request.id = :requestId and request.rfq.id = :rfqId")
    Optional<ReviewerReassignmentRequest> findByIdAndRfqIdForUpdate(@Param("requestId") Long requestId, @Param("rfqId") Long rfqId);
}
