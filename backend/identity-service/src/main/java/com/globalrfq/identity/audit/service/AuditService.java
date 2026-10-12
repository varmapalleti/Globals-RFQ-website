package com.globalrfq.identity.audit.service;

public interface AuditService {
    void record(AuditEvent event);
}
