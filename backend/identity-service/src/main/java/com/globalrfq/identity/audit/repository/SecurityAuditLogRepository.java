package com.globalrfq.identity.audit.repository;



import com.globalrfq.identity.audit.entity.SecurityAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;

public interface SecurityAuditLogRepository extends JpaRepository<SecurityAuditLog, Long>, JpaSpecificationExecutor<SecurityAuditLog> {
    @Modifying
    @Query("delete from SecurityAuditLog entry where entry.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
