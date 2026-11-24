package com.globalrfq.rfq.intake.repository;

import com.globalrfq.rfq.intake.entity.Rfq;
import com.globalrfq.rfq.intake.entity.RfqStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.time.Instant;
import java.util.List;

public interface RfqRepository extends JpaRepository<Rfq, Long> {
    boolean existsBySourceSystemAndSourceRfqId(String sourceSystem, String sourceRfqId);

    Optional<Rfq> findByPublicId(String publicId);

    @EntityGraph(attributePaths = "lineItems")
    Optional<Rfq> findWithLineItemsByPublicId(String publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Rfq r where r.publicId = :publicId")
    Optional<Rfq> findByPublicIdForUpdate(@Param("publicId") String publicId);

    Page<Rfq> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query(value = "select r from Rfq r where (:status is null or r.status = :status) "
            + "and (:search is null or lower(r.sourceRfqId) like lower(concat('%', :search, '%')) "
            + "or lower(r.customerName) like lower(concat('%', :search, '%')) "
            + "or lower(r.requestTitle) like lower(concat('%', :search, '%'))) ",
            countQuery = "select count(r.id) from Rfq r where (:status is null or r.status = :status) "
                    + "and (:search is null or lower(r.sourceRfqId) like lower(concat('%', :search, '%')) "
                    + "or lower(r.customerName) like lower(concat('%', :search, '%')) "
                    + "or lower(r.requestTitle) like lower(concat('%', :search, '%'))) ")
    Page<Rfq> searchAll(@Param("search") String search, @Param("status") RfqStatus status,
                        Pageable pageable);

    @Query(value = "select r from Rfq r where r.sourceRfqId = :sourceRfqId "
            + "order by r.createdAt desc",
            countQuery = "select count(r.id) from Rfq r where r.sourceRfqId = :sourceRfqId")
    Page<Rfq> findBySourceRfqId(@Param("sourceRfqId") String sourceRfqId, Pageable pageable);

    @Query(value = "select distinct r from Rfq r join RfqAssignment a on a.rfq = r "
            + "where a.assignedUserId = :userId order by r.createdAt desc",
            countQuery = "select count(distinct r.id) from Rfq r join RfqAssignment a on a.rfq = r "
                    + "where a.assignedUserId = :userId")
    Page<Rfq> findAssignedToUser(@Param("userId") Long userId, Pageable pageable);

    @Query("select r.status as status, count(distinct r.id) as amount from Rfq r "
            + "join RfqAssignment a on a.rfq = r where a.assignedUserId = :userId "
            + "group by r.status")
    List<RfqStatusCount> countStatusesForAssignedUser(@Param("userId") Long userId);

    @Query("select count(distinct r.id) from Rfq r join RfqAssignment a on a.rfq = r "
            + "where a.assignedUserId = :userId")
    long countAssignedToUser(@Param("userId") Long userId);

    @Query(value = "select distinct r from Rfq r join RfqAssignment a on a.rfq = r "
            + "where a.assignedUserId = :userId and (:status is null or r.status = :status) "
            + "and (:search is null or lower(r.sourceRfqId) like lower(concat('%', :search, '%')) "
            + "or lower(r.customerName) like lower(concat('%', :search, '%')) "
            + "or lower(r.requestTitle) like lower(concat('%', :search, '%'))) ",
            countQuery = "select count(distinct r.id) from Rfq r join RfqAssignment a on a.rfq = r "
                    + "where a.assignedUserId = :userId and (:status is null or r.status = :status) "
                    + "and (:search is null or lower(r.sourceRfqId) like lower(concat('%', :search, '%')) "
                    + "or lower(r.customerName) like lower(concat('%', :search, '%')) "
                    + "or lower(r.requestTitle) like lower(concat('%', :search, '%'))) ")
    Page<Rfq> searchAssignedToUser(@Param("userId") Long userId, @Param("search") String search,
                                  @Param("status") RfqStatus status, Pageable pageable);

    @Query(value = "select distinct r from Rfq r join RfqAssignment a on a.rfq = r "
            + "where a.assignedUserId = :userId and r.sourceRfqId = :sourceRfqId "
            + "order by r.createdAt desc",
            countQuery = "select count(distinct r.id) from Rfq r join RfqAssignment a on a.rfq = r "
                    + "where a.assignedUserId = :userId and r.sourceRfqId = :sourceRfqId")
    Page<Rfq> findAssignedToUserBySourceRfqId(@Param("userId") Long userId,
                                              @Param("sourceRfqId") String sourceRfqId,
                                              Pageable pageable);

    @Query("select r from Rfq r where (:status is null or r.status = :status) "
            + "and (:from is null or r.createdAt >= :from) and (:to is null or r.createdAt < :to) "
            + "order by r.createdAt desc")
    Page<Rfq> findForReport(@Param("status") RfqStatus status, @Param("from") Instant from,
                            @Param("to") Instant to, Pageable pageable);

    @Query("select r.status as status, count(r.id) as amount from Rfq r "
            + "where (:status is null or r.status = :status) and (:from is null or r.createdAt >= :from) "
            + "and (:to is null or r.createdAt < :to) group by r.status")
    List<RfqStatusCount> countStatusesForReport(@Param("status") RfqStatus status,
                                                @Param("from") Instant from, @Param("to") Instant to);

    @Query("select r.customerDecision as decision, count(r.id) as amount from Rfq r "
            + "where (:status is null or r.status = :status) and (:from is null or r.createdAt >= :from) "
            + "and (:to is null or r.createdAt < :to) group by r.customerDecision")
    List<CustomerDecisionCount> countCustomerDecisionsForReport(@Param("status") RfqStatus status,
                                                                @Param("from") Instant from, @Param("to") Instant to);

    interface RfqStatusCount { RfqStatus getStatus(); Long getAmount(); }
    interface CustomerDecisionCount { com.globalrfq.rfq.intake.entity.CustomerDecision getDecision(); Long getAmount(); }
}
