package com.iraychev.expenseanalyzer.repository;

import com.iraychev.expenseanalyzer.domain.entity.Transaction;
import com.iraychev.expenseanalyzer.domain.enums.CategorySource;
import com.iraychev.expenseanalyzer.domain.enums.CategoryStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
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

    /** All transactions of a user with the given merchant key. */
    @Query("""
            select t from Transaction t
            where t.bankAccount.bankConnection.user.id = :userId and t.merchant = :merchant
            """)
    List<Transaction> findByUserAndMerchant(@Param("userId") Long userId, @Param("merchant") String merchant);

    /** Rows of a user that were stored before merchants were parsed. */
    @Query("""
            select t from Transaction t
            where t.bankAccount.bankConnection.user.id = :userId and t.merchant is null
            """)
    List<Transaction> findByUserWithoutMerchant(@Param("userId") Long userId);

    /** FAILED / UNCATEGORIZABLE rows whose "try again no earlier than" time has come. */
    @Query("""
            select t.id from Transaction t
            where t.categoryStatus = :status and t.nextRetryAt is not null and t.nextRetryAt <= :now
            order by t.id
            """)
    List<Long> findRetryDueIds(@Param("status") CategoryStatus status, @Param("now") LocalDateTime now,
                               Pageable pageable);

    /** Automatically categorized rows produced by an older rules/prompt version (legacy rows have no version). */
    @Query("""
            select t.id from Transaction t
            where t.categoryStatus = :status
              and (t.categorySource is null or t.categorySource <> :user)
              and (t.categorizerVersion is null or t.categorizerVersion < :version)
            order by t.id
            """)
    List<Long> findOutdatedIds(@Param("status") CategoryStatus status, @Param("user") CategorySource user,
                               @Param("version") int version, Pageable pageable);

    /** Puts rows back in the worker queue, keeping their failure count (so failures keep backing off). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Transaction t set t.categoryStatus = :pending, t.nextRetryAt = null, t.claimedAt = null
            where t.id in :ids
            """)
    int requeueKeepingRetries(@Param("ids") Collection<Long> ids, @Param("pending") CategoryStatus pending);

    /** Puts rows back in the worker queue with a fresh failure count. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Transaction t set t.categoryStatus = :pending, t.retryCount = 0, t.nextRetryAt = null,
                t.claimedAt = null
            where t.id in :ids
            """)
    int requeueResettingRetries(@Param("ids") Collection<Long> ids, @Param("pending") CategoryStatus pending);

    @Query("select t.categoryStatus, t.categorySource, count(t) from Transaction t group by t.categoryStatus, t.categorySource")
    List<Object[]> countByStatusAndSource();
}
