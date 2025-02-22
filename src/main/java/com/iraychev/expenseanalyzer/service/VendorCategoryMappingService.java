package com.iraychev.expenseanalyzer.service;

import com.iraychev.expenseanalyzer.domain.entity.VendorCategoryMapping;
import com.iraychev.expenseanalyzer.repository.VendorCategoryMappingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class VendorCategoryMappingService {

    @Autowired
    private VendorCategoryMappingRepository vendorCategoryMappingRepository;

    public Optional<String> getCategoryForVendor(String vendor) {
        return vendorCategoryMappingRepository.findByVendor(vendor)
                .map(VendorCategoryMapping::getCategory);
    }

    public VendorCategoryMapping save(VendorCategoryMapping mapping) {
        return vendorCategoryMappingRepository.save(mapping);
    }
}