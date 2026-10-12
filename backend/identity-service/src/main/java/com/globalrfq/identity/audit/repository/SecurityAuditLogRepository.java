package com.globalrfq.identity.audit.repository;



import com.globalrfq.identity.audit.entity.SecurityAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityAuditLogRepository extends JpaRepository<SecurityAuditLog, Long> {}
