package com.globalrfq.rfq.attachment.repository;

import com.globalrfq.rfq.attachment.entity.RfqAttachment;
import com.globalrfq.rfq.attachment.entity.AttachmentScanState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RfqAttachmentRepository extends JpaRepository<RfqAttachment, Long> {
    List<RfqAttachment> findByRfqIdOrderByCreatedAtAsc(Long rfqId);
    Optional<RfqAttachment> findByPublicIdAndRfqId(String publicId, Long rfqId);
    Optional<RfqAttachment> findByPublicId(String publicId);
    List<RfqAttachment> findTop100ByScanStateOrderByCreatedAtAsc(AttachmentScanState scanState);
}
