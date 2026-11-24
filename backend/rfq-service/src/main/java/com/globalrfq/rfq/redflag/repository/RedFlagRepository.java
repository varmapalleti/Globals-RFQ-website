package com.globalrfq.rfq.redflag.repository;

import com.globalrfq.rfq.redflag.entity.RedFlag;
import com.globalrfq.rfq.redflag.entity.RedFlagSeverity;
import com.globalrfq.rfq.redflag.entity.RedFlagStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RedFlagRepository extends JpaRepository<RedFlag, Long> {

    @Query("SELECT f FROM RedFlag f WHERE " +
            "(:status IS NULL OR f.status = :status) AND " +
            "(:severity IS NULL OR f.severity = :severity) " +
            "ORDER BY f.createdAt DESC")
    Page<RedFlag> findFiltered(@Param("status") RedFlagStatus status,
                               @Param("severity") RedFlagSeverity severity,
                               Pageable pageable);

    @Query("SELECT f FROM RedFlag f WHERE " +
            "(f.raisedBy = :userId OR f.assignedTo = :userId) AND " +
            "(:status IS NULL OR f.status = :status) AND " +
            "(:severity IS NULL OR f.severity = :severity) " +
            "ORDER BY f.createdAt DESC")
    Page<RedFlag> findFilteredForUser(@Param("userId") Long userId,
                                      @Param("status") RedFlagStatus status,
                                      @Param("severity") RedFlagSeverity severity,
                                      Pageable pageable);

    long countByStatus(RedFlagStatus status);

    long countByStatusAndAssignedToOrStatusAndRaisedBy(RedFlagStatus s1, Long assignedTo, RedFlagStatus s2, Long raisedBy);
}
