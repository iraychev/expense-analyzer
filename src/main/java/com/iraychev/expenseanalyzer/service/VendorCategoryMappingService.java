package com.iraychev.expenseanalyzer.service;

import com.iraychev.expenseanalyzer.domain.entity.VendorCategoryMapping;
import com.iraychev.expenseanalyzer.repository.VendorCategoryMappingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

@Service
public class VendorCategoryMappingService {

    @Autowired
    private VendorCategoryMappingRepository vendorCategoryMappingRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    public Optional<VendorCategoryMapping> getMappingForVendor(String vendor) {
        return vendorCategoryMappingRepository.findByVendor(vendor);
    }

    public Optional<String> getCategoryForVendor(String vendor) {
        return getMappingForVendor(vendor)
                .map(VendorCategoryMapping::getCategory);
    }

    /**
     * Saves the mapping. Runs in its own transaction so that losing a race against a concurrent insert of the same
     * vendor (unique index) does not poison the caller's transaction: in that case the existing row is updated.
     */
    public VendorCategoryMapping save(VendorCategoryMapping mapping) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            return template.execute(status -> vendorCategoryMappingRepository.save(mapping));
        } catch (DataIntegrityViolationException e) {
            if (mapping.getId() != null || mapping.getVendor() == null) {
                throw e;
            }
            return template.execute(status -> {
                VendorCategoryMapping existing = vendorCategoryMappingRepository.findByVendor(mapping.getVendor())
                        .orElseThrow(() -> e);
                existing.setCategory(mapping.getCategory());
                return vendorCategoryMappingRepository.save(existing);
            });
        }
    }
}
