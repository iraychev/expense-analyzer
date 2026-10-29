package com.iraychev.expenseanalyzer.repository;

import com.iraychev.expenseanalyzer.domain.entity.Transaction;
import com.iraychev.expenseanalyzer.domain.enums.CategoryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByBankAccountId(Long id);

    /**
     * Locks due PENDING rows with {@code FOR UPDATE SKIP LOCKED} so that concurrent workers (or instances) never
     * claim the same rows. Rows whose claim lease has not expired are not due. Native SQL because it must work the
     * same on PostgreSQL and H2; {h-schema} resolves the configured default schema.
     */
    @Query(value = """
            select t.* from {h-schema}transactions t
            where t.category_status = 'PENDING'
              and (t.other_next_retry_at is null or t.other_next_retry_at <= :now)
              and (t.claimed_at is null or t.claimed_at < :leaseExpiredBefore)
            order by t.id
            limit :limit
            for update skip locked
            """, nativeQuery = true)
    List<Transaction> lockDuePending(@Param("now") LocalDateTime now,
                                     @Param("leaseExpiredBefore") LocalDateTime leaseExpiredBefore,
                                     @Param("limit") int limit);

    long countByCategoryStatus(CategoryStatus status);
}
