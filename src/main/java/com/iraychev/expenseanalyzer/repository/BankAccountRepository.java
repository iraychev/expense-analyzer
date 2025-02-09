package com.iraychev.expenseanalyzer.repository;

import com.iraychev.expenseanalyzer.entity.BankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {
    List<BankAccount> findByUserId(Long userId);
    Optional<BankAccount> findByAccountId(String accountId);
    Optional<BankAccount> findByUserIdAndStatus(Long userId, String status);
    Optional<BankAccount> findByAccountIdAndUserId(String accountId, Long userId);
}