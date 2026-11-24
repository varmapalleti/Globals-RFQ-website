package com.globalrfq.rfq.attachment.repository;

import com.globalrfq.rfq.attachment.entity.AttachmentScanEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentScanEventRepository extends JpaRepository<AttachmentScanEvent, Long> { }
