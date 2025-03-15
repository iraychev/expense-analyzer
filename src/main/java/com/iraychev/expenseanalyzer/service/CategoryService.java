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
    private final VendorCategoryMappingService vendorCategoryMappingService;
    private final AiCategorizationService aiCategorizationService;

    public String categorizeTransaction(String remittanceInfo) {
        String vendor = extractVendorFromRemittanceInfo(remittanceInfo.toLowerCase());
        Optional<String> categoryOpt = vendorCategoryMappingService.getCategoryForVendor(vendor);
        if (categoryOpt.isPresent()) {
            return categoryOpt.get();
        } else {
            log.info("Vendor {} not found in the database, categorizing...", vendor);
            String aiCategory = aiCategorizationService.categorizeRemittance(remittanceInfo);

            vendorCategoryMappingService.save(VendorCategoryMapping.builder().category(aiCategory).vendor(vendor).build());
            return aiCategory;
        }
    }

    private String extractVendorFromRemittanceInfo(String remittanceInfo) {
        String pattern = "авт\\.код:\\d+-([A-Za-z\\s]+)";
        Pattern regexPattern = Pattern.compile(pattern);
        Matcher matcher = regexPattern.matcher(remittanceInfo);
        if (matcher.find()) {
            return matcher.group(1).trim();
        } else {
            return "Unknown Vendor";
        }
    }
}
