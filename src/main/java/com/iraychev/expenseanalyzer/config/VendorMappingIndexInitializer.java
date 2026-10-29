package com.iraychev.expenseanalyzer.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Makes {@code vendor_category_mapping.vendor} unique on databases that already contain duplicates.
 * <p>
 * The schema is managed by {@code ddl-auto=update}, which cannot create a unique constraint over duplicate rows (the
 * application would fail to start). So the entity is deliberately not annotated unique; instead this component runs
 * once all beans (and therefore the Hibernate schema update) are ready but before the web server accepts requests:
 * it removes the "Unknown Vendor" placeholder rows, merges duplicate vendors (keeping the row with a real category,
 * lowest id first) and then creates the unique index if it is missing. It is idempotent and never prevents startup.
 */
@Component
@Slf4j
public class VendorMappingIndexInitializer implements SmartInitializingSingleton {

    static final String INDEX_NAME = "uk_vendor_category_mapping_vendor";
    private static final String OTHER = "other";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactionTemplate;
    private final String table;

    public VendorMappingIndexInitializer(JdbcTemplate jdbc,
                                         PlatformTransactionManager transactionManager,
                                         @Value("${spring.jpa.properties.hibernate.default_schema:}") String schema) {
        this.jdbc = jdbc;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.table = (schema == null || schema.isBlank() ? "" : schema + ".") + "vendor_category_mapping";
    }

    @Override
    public void afterSingletonsInstantiated() {
        try {
            run();
        } catch (RuntimeException e) {
            // Degraded but working (as before this change): do not take the application down.
            log.error("Could not enforce uniqueness of {}.vendor; continuing without the unique index", table, e);
        }
    }

    /** Cleans the table and creates the unique index. Safe to call repeatedly. */
    public void run() {
        transactionTemplate.executeWithoutResult(status -> {
            int placeholders = jdbc.update("delete from " + table + " where lower(vendor) = 'unknown vendor'");
            int merged = mergeDuplicateVendors();
            if (placeholders > 0 || merged > 0) {
                log.info("vendor_category_mapping cleanup: removed {} placeholder row(s) and {} duplicate row(s)",
                        placeholders, merged);
            }
        });
        jdbc.execute("create unique index if not exists " + INDEX_NAME + " on " + table + " (vendor)");
    }

    private int mergeDuplicateVendors() {
        Map<String, List<Row>> byVendor = new LinkedHashMap<>();
        jdbc.query("select id, vendor, category from " + table + " where vendor is not null order by id", rs -> {
            byVendor.computeIfAbsent(rs.getString("vendor"), v -> new ArrayList<>())
                    .add(new Row(rs.getLong("id"), rs.getString("category")));
        });

        List<Object[]> deletions = new ArrayList<>();
        for (List<Row> rows : byVendor.values()) {
            if (rows.size() < 2) {
                continue;
            }
            Row keeper = rows.stream().filter(Row::hasRealCategory).findFirst().orElse(rows.get(0));
            rows.stream().filter(r -> r != keeper).forEach(r -> deletions.add(new Object[]{r.id()}));
        }
        if (!deletions.isEmpty()) {
            jdbc.batchUpdate("delete from " + table + " where id = ?", deletions);
        }
        return deletions.size();
    }

    private record Row(long id, String category) {
        boolean hasRealCategory() {
            return category != null && !category.isBlank() && !OTHER.equalsIgnoreCase(category.trim());
        }
    }
}
