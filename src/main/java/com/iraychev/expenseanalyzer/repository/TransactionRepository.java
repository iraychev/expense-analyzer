package com.iraychev.expenseanalyzer.repository;

import com.iraychev.expenseanalyzer.domain.entity.Transaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByBankAccountId(Long id);

    @Query("""
            select t from Transaction t
            where lower(t.category) = 'other'
              and (t.otherRetryCount is null or t.otherRetryCount < :maxRetries)
              and (t.otherNextRetryAt is null or t.otherNextRetryAt <= :now)
            order by t.otherNextRetryAt asc
            """)
    List<Transaction> findDueOtherRetries(@Param("now") LocalDateTime now,
                                          @Param("maxRetries") int maxRetries,
                                          Pageable pageable);
}