package com.globalrfq.rfq.redflag.repository;

import com.globalrfq.rfq.redflag.entity.RedFlag;
import com.globalrfq.rfq.redflag.entity.RedFlagSeverity;
import com.globalrfq.rfq.redflag.entity.RedFlagStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RedFlagRepository extends JpaRepository<RedFlag, Long> {

    @Query("SELECT f FROM RedFlag f WHERE " +
            "(:status IS NULL OR f.status = :status) AND " +
            "(:severity IS NULL OR f.severity = :severity) " +
            "ORDER BY f.createdAt DESC")
    Page<RedFlag> findFiltered(RedFlagStatus status, RedFlagSeverity severity, Pageable pageable);

    long countByStatus(RedFlagStatus status);
}
