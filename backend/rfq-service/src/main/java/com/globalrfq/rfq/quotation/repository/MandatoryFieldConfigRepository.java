package com.globalrfq.rfq.quotation.repository;

import com.globalrfq.rfq.quotation.entity.MandatoryFieldConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MandatoryFieldConfigRepository extends JpaRepository<MandatoryFieldConfig, String> {
    List<MandatoryFieldConfig> findAllByRequiredTrueOrderByFieldKeyAsc();
}
