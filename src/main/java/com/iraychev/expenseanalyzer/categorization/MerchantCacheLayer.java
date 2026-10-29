package com.iraychev.expenseanalyzer.categorization;

import com.iraychev.expenseanalyzer.domain.entity.VendorCategoryMapping;
import com.iraychev.expenseanalyzer.domain.enums.CategorySource;
import com.iraychev.expenseanalyzer.repository.VendorCategoryMappingRepository;
import com.iraychev.expenseanalyzer.service.VendorCategoryMappingService;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Layer 2: exact merchant key lookup in the vendor mapping table (filled from earlier AI answers). */
@Component
public class MerchantCacheLayer implements CategorizationLayer {

    private final VendorCategoryMappingRepository repository;
    private final VendorCategoryMappingService mappingService;

    public MerchantCacheLayer(VendorCategoryMappingRepository repository, VendorCategoryMappingService mappingService) {
        this.repository = repository;
        this.mappingService = mappingService;
    }

    @Override
    public Map<Integer, Decision> resolve(List<CategorizationRequest> requests) {
        Set<String> keys = new HashSet<>();
        for (CategorizationRequest request : requests) {
            String key = request.parsed().cacheKey();
            if (key != null) {
                keys.add(key);
            }
        }
        if (keys.isEmpty()) {
            return Map.of();
        }
        Map<String, Category> known = new HashMap<>();
        for (VendorCategoryMapping mapping : repository.findByVendorIn(keys)) {
            Category category = Category.fromKey(mapping.getCategory()).orElse(null);
            if (category != null && category != Category.OTHER) {
                known.put(mapping.getVendor(), category);
            }
        }
        Map<Integer, Decision> result = new HashMap<>();
        for (int i = 0; i < requests.size(); i++) {
            String key = requests.get(i).parsed().cacheKey();
            Category category = key == null ? null : known.get(key);
            if (category != null) {
                result.put(i, new Decision(category, CategorySource.CACHE));
            }
        }
        return result;
    }

    /** Remembers an AI answer for a merchant key. */
    public void remember(String cacheKey, Category category) {
        if (cacheKey == null || category == null || category == Category.OTHER) {
            return;
        }
        VendorCategoryMapping mapping = repository.findByVendor(cacheKey)
                .orElseGet(() -> VendorCategoryMapping.builder().vendor(cacheKey).build());
        mapping.setCategory(category.key());
        mappingService.save(mapping);
    }
}
