package com.iraychev.expenseanalyzer.repository;

import com.iraychev.expenseanalyzer.domain.entity.VendorCategoryMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VendorCategoryMappingRepository extends JpaRepository<VendorCategoryMapping, Long> {
    Optional<VendorCategoryMapping> findByVendor(String vendor);
}