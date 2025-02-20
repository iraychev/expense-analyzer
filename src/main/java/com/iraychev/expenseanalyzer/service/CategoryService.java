package com.iraychev.expenseanalyzer.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CategoryService {

    @Autowired
    private VendorCategoryMappingService vendorCategoryMappingService;

    public String categorizeTransaction(String remittanceInfo) {
        String vendor = extractVendorFromRemittanceInfo(remittanceInfo.toLowerCase());
        Optional<String> categoryOpt = vendorCategoryMappingService.getCategoryForVendor(vendor);
        return categoryOpt.orElse("Uncategorized");
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
