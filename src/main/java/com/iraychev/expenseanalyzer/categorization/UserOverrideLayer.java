package com.iraychev.expenseanalyzer.categorization;

import com.iraychev.expenseanalyzer.domain.entity.UserCategoryRule;
import com.iraychev.expenseanalyzer.domain.enums.CategorySource;
import com.iraychev.expenseanalyzer.repository.UserCategoryRuleRepository;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Layer 1: the user's own merchant to category rules always win. */
@Component
public class UserOverrideLayer implements CategorizationLayer {

    private final UserCategoryRuleRepository rules;

    public UserOverrideLayer(UserCategoryRuleRepository rules) {
        this.rules = rules;
    }

    @Override
    public Map<Integer, Decision> resolve(List<CategorizationRequest> requests) {
        Set<Long> userIds = new HashSet<>();
        Set<String> merchants = new HashSet<>();
        for (CategorizationRequest request : requests) {
            if (request.userId() != null && request.parsed().merchantKey() != null) {
                userIds.add(request.userId());
                merchants.add(request.parsed().merchantKey());
            }
        }
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<String, Category> byUserAndMerchant = new HashMap<>();
        for (UserCategoryRule rule : rules.findByUserIdInAndMerchantIn(userIds, merchants)) {
            Optional<Category> category = Category.fromKey(rule.getCategory());
            category.ifPresent(c -> byUserAndMerchant.put(rule.getUserId() + "|" + rule.getMerchant(), c));
        }
        Map<Integer, Decision> result = new HashMap<>();
        for (int i = 0; i < requests.size(); i++) {
            CategorizationRequest request = requests.get(i);
            if (request.userId() == null || request.parsed().merchantKey() == null) {
                continue;
            }
            Category category = byUserAndMerchant.get(request.userId() + "|" + request.parsed().merchantKey());
            if (category != null) {
                result.put(i, new Decision(category, CategorySource.USER));
            }
        }
        return result;
    }
}
