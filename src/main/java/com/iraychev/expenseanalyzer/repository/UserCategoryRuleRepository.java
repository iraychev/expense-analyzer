package com.iraychev.expenseanalyzer.repository;

import com.iraychev.expenseanalyzer.domain.entity.UserCategoryRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserCategoryRuleRepository extends JpaRepository<UserCategoryRule, Long> {
    Optional<UserCategoryRule> findByUserIdAndMerchant(Long userId, String merchant);

    List<UserCategoryRule> findByUserIdInAndMerchantIn(Collection<Long> userIds, Collection<String> merchants);
}
