package com.iraychev.expenseanalyzer.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Gives rows stored before category statuses existed (status is null) a status, once, at startup:
 * <ul>
 *   <li>"Other" (or empty) rows that still had retries left go back to PENDING with a fresh retry counter so the
 *       worker re-evaluates them with the new pipeline;</li>
 *   <li>"Other" rows that had used up their old retries become UNCATEGORIZABLE (retried rarely by the nightly job);</li>
 *   <li>everything else is CATEGORIZED. Legacy rows get categorizer version 0 so they count as outdated.</li>
 * </ul>
 * The old retry counter and timestamp columns are reused as the new retry counter / next attempt time.
 * Idempotent: only rows with a null status are touched.
 */
@Component
@Slf4j
public class LegacyCategoryMigration implements SmartInitializingSingleton {

    private static final int LEGACY_MAX_RETRIES = 3;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactionTemplate;
    private final String table;

    public LegacyCategoryMigration(JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
                                   @Value("${spring.jpa.properties.hibernate.default_schema:}") String schema) {
        this.jdbc = jdbc;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.table = (schema == null || schema.isBlank() ? "" : schema + ".") + "transactions";
    }

    @Override
    public void afterSingletonsInstantiated() {
        try {
            run();
        } catch (RuntimeException e) {
            log.error("Legacy category migration failed; rows without a status will be treated as uncategorized", e);
        }
    }

    public void run() {
        transactionTemplate.executeWithoutResult(status -> {
            int pending = jdbc.update("update " + table + " set category_status = 'PENDING', other_retry_count = 0, "
                    + "other_next_retry_at = null, claimed_at = null "
                    + "where category_status is null and (category is null or lower(category) = 'other') "
                    + "and coalesce(other_retry_count, 0) < ?", LEGACY_MAX_RETRIES);
            int uncategorizable = jdbc.update("update " + table + " set category_status = 'UNCATEGORIZABLE', "
                    + "categorizer_version = 0, other_next_retry_at = null "
                    + "where category_status is null and (category is null or lower(category) = 'other')");
            int categorized = jdbc.update("update " + table + " set category_status = 'CATEGORIZED', "
                    + "categorizer_version = 0, other_next_retry_at = null where category_status is null");
            if (pending + uncategorizable + categorized > 0) {
                log.info("Legacy category migration: {} pending, {} uncategorizable, {} categorized",
                        pending, uncategorizable, categorized);
            }
        });
    }
}
