package com.iraychev.expenseanalyzer.repository;

import com.iraychev.expenseanalyzer.domain.entity.VendorCategoryMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VendorCategoryMappingRepository extends JpaRepository<VendorCategoryMapping, Long> {
    Optional<VendorCategoryMapping> findByVendor(String vendor);

    List<VendorCategoryMapping> findByVendorIn(Collection<String> vendors);
}