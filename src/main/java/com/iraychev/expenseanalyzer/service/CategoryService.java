package com.iraychev.expenseanalyzer.service;

import com.iraychev.expenseanalyzer.domain.entity.VendorCategoryMapping;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CategoryService {
    private static final Logger log = LoggerFactory.getLogger(CategoryService.class);
    private static final String OTHER_CATEGORY = "Other";
    // Card payments look like "ПОС 6.28 BGN авт.код:762106-BILLA 198 04/SOFIA/PAN:...": the merchant is up to the first "/"
    private static final Pattern VENDOR_PATTERN = Pattern.compile("авт\\.код:\\d+-([^/,]+)");

    private final VendorCategoryMappingService vendorCategoryMappingService;
    private final AiCategorizationService aiCategorizationService;

    public String categorizeTransaction(String remittanceInfo) {
        return categorizeTransactions(List.of(remittanceInfo == null ? "" : remittanceInfo)).get(0);
    }

    /**
     * Categorizes the descriptions from the vendor cache where possible and sends the rest to the AI in one
     * batch (one entry per distinct vendor). The result has the same size and order as the input.
     */
    public List<String> categorizeTransactions(List<String> descriptions) {
        List<String> result = new ArrayList<>(Collections.nCopies(descriptions.size(), OTHER_CATEGORY));
        // key -> indexes still to categorize; a vendor key when known, otherwise the description itself
        Map<String, List<Integer>> pending = new LinkedHashMap<>();
        Map<String, Optional<VendorCategoryMapping>> mappings = new LinkedHashMap<>();

        for (int i = 0; i < descriptions.size(); i++) {
            String description = descriptions.get(i);
            if (description == null || description.isBlank()) {
                continue;
            }
            String vendor = extractVendor(description);
            if (vendor == null) {
                pending.computeIfAbsent(description, k -> new ArrayList<>()).add(i);
                continue;
            }
            Optional<VendorCategoryMapping> mapping = mappings.computeIfAbsent(vendor,
                    vendorCategoryMappingService::getMappingForVendor);
            if (mapping.isPresent() && !OTHER_CATEGORY.equalsIgnoreCase(mapping.get().getCategory())) {
                result.set(i, mapping.get().getCategory());
            } else {
                pending.computeIfAbsent(vendor, k -> new ArrayList<>()).add(i);
            }
        }

        if (pending.isEmpty()) {
            return result;
        }

        List<String> keys = new ArrayList<>(pending.keySet());
        List<String> toAsk = keys.stream().map(key -> descriptions.get(pending.get(key).get(0))).toList();
        log.info("Categorizing {} distinct vendors with the AI", keys.size());
        List<String> answers = aiCategorizationService.categorizeRemittances(toAsk);

        for (int k = 0; k < keys.size(); k++) {
            String key = keys.get(k);
            String category = answers.get(k);
            pending.get(key).forEach(index -> result.set(index, category));

            // Only real vendors are cached; descriptions without a vendor never share a category.
            if (mappings.containsKey(key) && !OTHER_CATEGORY.equalsIgnoreCase(category)) {
                VendorCategoryMapping mapping = mappings.get(key).orElseGet(
                        () -> VendorCategoryMapping.builder().vendor(key).build());
                mapping.setCategory(category);
                vendorCategoryMappingService.save(mapping);
            }
        }
        return result;
    }

    /** The lower-cased merchant of a card payment, or null if the description has none. */
    static String extractVendor(String description) {
        Matcher matcher = VENDOR_PATTERN.matcher(description.toLowerCase());
        if (!matcher.find()) {
            return null;
        }
        String vendor = matcher.group(1).trim().replaceAll("\\s+", " ");
        return vendor.isEmpty() ? null : vendor;
    }
}
