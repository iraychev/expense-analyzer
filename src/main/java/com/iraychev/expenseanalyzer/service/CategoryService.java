package com.iraychev.expenseanalyzer.service;

import com.iraychev.expenseanalyzer.domain.entity.VendorCategoryMapping;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CategoryService {
    private static final Logger log = LoggerFactory.getLogger(CategoryService.class);
    private static final String OTHER_CATEGORY = "Other";
    static final String UNKNOWN_VENDOR = "Unknown Vendor";
    private final VendorCategoryMappingService vendorCategoryMappingService;
    private final AiCategorizationService aiCategorizationService;

    public String categorizeTransaction(String remittanceInfo) {
        if (remittanceInfo == null || remittanceInfo.isBlank()) {
            return OTHER_CATEGORY;
        }

        String vendor = extractVendorFromRemittanceInfo(remittanceInfo.toLowerCase());
        // The placeholder is not a merchant: never read or write the cache under it.
        boolean cacheable = !UNKNOWN_VENDOR.equals(vendor);
        Optional<VendorCategoryMapping> mappingOpt = cacheable
                ? vendorCategoryMappingService.getMappingForVendor(vendor)
                : Optional.empty();
        if (mappingOpt.isPresent() && !OTHER_CATEGORY.equalsIgnoreCase(mappingOpt.get().getCategory())) {
            return mappingOpt.get().getCategory();
        }

        log.info("Vendor {} not found in the database, categorizing...", vendor);
        String aiCategory = aiCategorizationService.categorizeRemittance(remittanceInfo);

        if (cacheable && !OTHER_CATEGORY.equalsIgnoreCase(aiCategory)) {
            VendorCategoryMapping mapping = mappingOpt.orElseGet(
                    () -> VendorCategoryMapping.builder().vendor(vendor).build()
            );
            mapping.setCategory(aiCategory);
            vendorCategoryMappingService.save(mapping);
        }
        return aiCategory;
    }

    private String extractVendorFromRemittanceInfo(String remittanceInfo) {
        String pattern = "авт\\.код:\\d+-([A-Za-z\\s]+)";
        Pattern regexPattern = Pattern.compile(pattern);
        Matcher matcher = regexPattern.matcher(remittanceInfo);
        if (matcher.find()) {
            return matcher.group(1).trim();
        } else {
            return UNKNOWN_VENDOR;
        }
    }
}
