package com.globalrfq.rfq.purchase.repository;

import com.globalrfq.rfq.purchase.entity.PurchaseOrder;
import com.globalrfq.rfq.purchase.entity.PurchaseOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    @Query("SELECT p FROM PurchaseOrder p WHERE " +
            "(:status IS NULL OR p.status = :status) " +
            "ORDER BY p.createdAt DESC")
    Page<PurchaseOrder> findFiltered(PurchaseOrderStatus status, Pageable pageable);

    Optional<PurchaseOrder> findByOrderId(String orderId);

    boolean existsByOrderId(String orderId);
}
