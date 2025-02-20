package com.iraychev.expenseanalyzer.service;

import com.example.expense.model.VendorCategoryMapping;
import com.example.expense.repository.VendorCategoryMappingRepository;
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