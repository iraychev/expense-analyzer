package com.iraychev.expenseanalyzer.categorization;

import com.iraychev.expenseanalyzer.categorization.ai.AiCategorizer;
import com.iraychev.expenseanalyzer.categorization.ai.AiItem;
import com.iraychev.expenseanalyzer.categorization.ai.AiUnavailableException;
import com.iraychev.expenseanalyzer.categorization.parser.DskParser;
import com.iraychev.expenseanalyzer.categorization.parser.RevolutParser;
import com.iraychev.expenseanalyzer.categorization.parser.TransactionParsers;
import com.iraychev.expenseanalyzer.categorization.parser.UniCreditParser;
import com.iraychev.expenseanalyzer.config.LegacyCategoryMigration;
import com.iraychev.expenseanalyzer.domain.entity.BankAccount;
import com.iraychev.expenseanalyzer.domain.entity.BankConnection;
import com.iraychev.expenseanalyzer.domain.entity.Transaction;
import com.iraychev.expenseanalyzer.domain.entity.User;
import com.iraychev.expenseanalyzer.domain.enums.CategorySource;
import com.iraychev.expenseanalyzer.domain.enums.CategoryStatus;
import com.iraychev.expenseanalyzer.repository.BankAccountRepository;
import com.iraychev.expenseanalyzer.repository.BankConnectionRepository;
import com.iraychev.expenseanalyzer.repository.TransactionRepository;
import com.iraychev.expenseanalyzer.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The worker against a real JPA schema (H2 in PostgreSQL mode) with a stubbed AI. */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:workerdb;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS expense_analyzer",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = CategorizationWorkerJpaTest.JpaTestConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CategorizationWorkerJpaTest {

    @Configuration
    @EnableAutoConfiguration
    @EntityScan("com.iraychev.expenseanalyzer.domain.entity")
    @EnableJpaRepositories("com.iraychev.expenseanalyzer.repository")
    static class JpaTestConfig {
    }

    @Autowired private TransactionRepository transactions;
    @Autowired private UserRepository users;
    @Autowired private BankConnectionRepository connections;
    @Autowired private BankAccountRepository accounts;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private JdbcTemplate jdbc;

    private final Map<String, String> aiAnswers = new HashMap<>();
    private final List<List<AiItem>> aiCalls = new java.util.concurrent.CopyOnWriteArrayList<>();
    private volatile AiUnavailableException aiFailure;
    private volatile Runnable duringAi = () -> { };

    private BankAccount account;
    private CategorizationWorker worker;

    private final AiCategorizer ai = items -> {
        aiCalls.add(items);
        duringAi.run();
        if (aiFailure != null) {
            throw aiFailure;
        }
        Map<String, String> out = new HashMap<>();
        items.forEach(i -> out.put(i.id(), aiAnswers.getOrDefault(i.text(), AiCategorizer.UNKNOWN)));
        return out;
    };

    @BeforeEach
    void setUp() {
        jdbc.update("delete from expense_analyzer.transactions");
        accounts.deleteAll();
        connections.deleteAll();
        users.deleteAll();

        User user = users.save(User.builder().name("Ivaylo Raychev").username("ivo" + System.nanoTime())
                .password("x").email("ivo" + System.nanoTime() + "@x.com").build());
        BankConnection connection = connections.save(BankConnection.builder().reference("r").institutionId("UNICREDIT_UNCRBGSF")
                .requisitionId("req" + System.nanoTime()).user(user).build());
        account = accounts.save(BankAccount.builder().accountId("a").iban("BG00").bankConnection(connection).build());
        aiAnswers.clear();
        aiCalls.clear();
        aiFailure = null;
        duringAi = () -> { };
        worker = newWorker(3, 10);
    }

    private CategorizationWorker newWorker(int maxRetries, int claimSize) {
        MerchantCacheLayer cache = mock(MerchantCacheLayer.class);
        when(cache.resolve(anyList())).thenReturn(Map.of());
        CategorizationPipeline pipeline = new CategorizationPipeline(new UserOverrideLayer(), cache, new RuleLayer(), ai, 25);
        TransactionParsers parsers = new TransactionParsers(new UniCreditParser(), new RevolutParser(), new DskParser());
        return new CategorizationWorker(transactions, parsers, pipeline, txManager, claimSize, 20, maxRetries,
                Duration.ofMinutes(5), Duration.ofMinutes(10), Duration.ofMinutes(5));
    }

    private Transaction pending(String merchant, String amount) {
        return transactions.save(Transaction.builder()
                .amount(new BigDecimal(amount)).currency("BGN")
                .transactionDate(LocalDateTime.now()).valueDate(LocalDateTime.now())
                .description("ПОС 1.00 BGN авт.код:123456-" + merchant + "/SOFIA/PAN:5169****9015/CT:08,Операция с карта")
                .bankTypeCode("AC1").category("Other").categoryStatus(CategoryStatus.PENDING).retryCount(0)
                .bankAccount(account).build());
    }

    private Transaction reload(Transaction tx) {
        return transactions.findById(tx.getId()).orElseThrow();
    }

    @Test
    void pendingRowsAreCategorizedInBatchesByRulesAndByOneAiCallPerMerchant() {
        Transaction billa = pending("BILLA 198 04", "-5");
        Transaction a1 = pending("ZLATEN KLAS 1996 OOD", "-7");
        Transaction a2 = pending("ZLATEN KLAS 1996 OOD", "-6");
        aiAnswers.put("Card payment at: ZLATEN KLAS 1996 OOD", "Supermarkets");

        CategorizationWorker.RunStats stats = worker.runNow();

        assertEquals(3, stats.claimed());
        assertEquals(3, stats.categorized());
        Transaction b = reload(billa);
        assertEquals(CategoryStatus.CATEGORIZED, b.getCategoryStatus());
        assertEquals(CategorySource.RULE, b.getCategorySource());
        assertEquals("Supermarkets", b.getCategory());
        assertEquals("billa 198 04", b.getMerchant());
        assertEquals(CategorizationPipeline.VERSION, b.getCategorizerVersion());
        assertNull(b.getClaimedAt());
        for (Transaction t : List.of(a1, a2)) {
            Transaction r = reload(t);
            assertEquals("Supermarkets", r.getCategory());
            assertEquals(CategorySource.AI, r.getCategorySource());
        }
        long askedMerchants = aiCalls.stream().mapToLong(List::size).sum();
        assertEquals(1, askedMerchants, "two rows of the same merchant are one AI item");
        assertEquals(0, transactions.countByCategoryStatus(CategoryStatus.PENDING));
    }

    @Test
    void modelSayingUnknownMakesTheRowUncategorizable() {
        Transaction t = pending("MYSTERY SHOP", "-3");

        worker.runNow();

        Transaction r = reload(t);
        assertEquals(CategoryStatus.UNCATEGORIZABLE, r.getCategoryStatus());
        assertEquals("Other", r.getCategory());
    }

    @Test
    void transientFailureKeepsRowPendingWithBackoffThenFailsAfterMaxRetries() {
        Transaction t = pending("MYSTERY SHOP", "-3");
        aiFailure = new AiUnavailableException("rate limited", 429);

        worker.runNow();
        Transaction first = reload(t);
        assertEquals(CategoryStatus.PENDING, first.getCategoryStatus());
        assertEquals(1, first.getRetryCount());
        assertNotNull(first.getNextRetryAt());
        assertTrue(first.getNextRetryAt().isAfter(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(4)));

        // not due yet: a second run does not even claim it
        assertEquals(0, worker.runNow().claimed());

        makeDue(t);
        worker.runNow();
        Transaction second = reload(t);
        assertEquals(2, second.getRetryCount());
        assertTrue(second.getNextRetryAt().isAfter(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(9)), "backoff doubles");

        makeDue(t);
        CategorizationWorker.RunStats last = worker.runNow();
        Transaction failed = reload(t);
        assertEquals(CategoryStatus.FAILED, failed.getCategoryStatus());
        assertEquals(3, failed.getRetryCount());
        assertNull(failed.getNextRetryAt());
        assertEquals(1, last.failed());

        // FAILED rows are no longer picked up by the worker
        assertEquals(0, worker.runNow().claimed());
    }

    @Test
    void recoveredAiCategorizesTheRetriedRow() {
        Transaction t = pending("MYSTERY SHOP", "-3");
        aiFailure = new AiUnavailableException("boom", 503);
        worker.runNow();
        aiFailure = null;
        aiAnswers.put("Card payment at: MYSTERY SHOP", "Shopping");
        makeDue(t);

        worker.runNow();

        assertEquals("Shopping", reload(t).getCategory());
        assertEquals(CategoryStatus.CATEGORIZED, reload(t).getCategoryStatus());
    }

    @Test
    void openCircuitDefersWithoutUsingUpRetries() {
        Transaction t = pending("MYSTERY SHOP", "-3");
        aiFailure = AiUnavailableException.circuitOpen();

        CategorizationWorker.RunStats stats = worker.runNow();

        Transaction r = reload(t);
        assertEquals(CategoryStatus.PENDING, r.getCategoryStatus());
        assertEquals(0, r.getRetryCount());
        assertNotNull(r.getNextRetryAt());
        assertEquals(1, stats.retryLater());
    }

    @Test
    void aRowDecidedWhileTheAiWasRunningIsNotOverwritten() {
        Transaction t = pending("MYSTERY SHOP", "-3");
        aiAnswers.put("Card payment at: MYSTERY SHOP", "Shopping");
        duringAi = () -> new TransactionTemplate(txManager).executeWithoutResult(s -> {
            Transaction row = transactions.findById(t.getId()).orElseThrow();
            row.setCategory("Health and Beauty");
            row.setCategoryStatus(CategoryStatus.CATEGORIZED);
            row.setCategorySource(CategorySource.USER);
        });

        worker.runNow();

        Transaction r = reload(t);
        assertEquals("Health and Beauty", r.getCategory());
        assertEquals(CategorySource.USER, r.getCategorySource());
    }

    @Test
    void rowsWithAFreshClaimAreSkippedAndRowsWithAnExpiredLeaseAreTakenOver() {
        Transaction fresh = pending("BILLA 1", "-1");
        Transaction stale = pending("LIDL 1", "-1");
        jdbc.update("update expense_analyzer.transactions set claimed_at = ? where id = ?", LocalDateTime.now(ZoneOffset.UTC), fresh.getId());
        jdbc.update("update expense_analyzer.transactions set claimed_at = ? where id = ?",
                LocalDateTime.now(ZoneOffset.UTC).minusHours(1), stale.getId());

        CategorizationWorker.RunStats stats = worker.runNow();

        assertEquals(1, stats.claimed());
        assertEquals(CategoryStatus.PENDING, reload(fresh).getCategoryStatus());
        assertEquals(CategoryStatus.CATEGORIZED, reload(stale).getCategoryStatus());
    }

    @Test
    void concurrentClaimsNeverReturnTheSameRowsBecauseLockedRowsAreSkipped() throws Exception {
        for (int i = 0; i < 4; i++) {
            pending("SHOP " + i, "-1");
        }
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch firstHasLocks = new CountDownLatch(1);
        CountDownLatch secondDone = new CountDownLatch(1);
        try {
            Future<Set<Long>> first = pool.submit(() -> new TransactionTemplate(txManager).execute(s -> {
                Set<Long> ids = lockTwo();
                firstHasLocks.countDown();
                await(secondDone);
                return ids;
            }));
            assertTrue(firstHasLocks.await(10, TimeUnit.SECONDS));
            Set<Long> second = pool.submit(() -> new TransactionTemplate(txManager).execute(s -> lockTwo()))
                    .get(10, TimeUnit.SECONDS);
            secondDone.countDown();
            Set<Long> firstIds = first.get(10, TimeUnit.SECONDS);

            assertEquals(2, firstIds.size());
            assertEquals(2, second.size());
            assertTrue(java.util.Collections.disjoint(firstIds, second), "workers must not claim the same rows");
        } finally {
            secondDone.countDown();
            pool.shutdownNow();
        }
    }

    private Set<Long> lockTwo() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return transactions.lockDuePending(now, now.minusMinutes(10), 2)
                .stream().map(Transaction::getId).collect(java.util.stream.Collectors.toSet());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void makeDue(Transaction t) {
        jdbc.update("update expense_analyzer.transactions set other_next_retry_at = ? where id = ?",
                LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1), t.getId());
    }

    // ---- legacy migration ------------------------------------------------------------------------------------

    @Test
    void legacyRowsGetAStatusAndOldOtherRowsAreRequeued() {
        Transaction ok = legacy("Supermarkets", 0);
        Transaction other = legacy("Other", 1);
        Transaction exhausted = legacy("Other", 3);

        LegacyCategoryMigration migration = new LegacyCategoryMigration(jdbc, txManager, "expense_analyzer");
        migration.run();
        migration.run(); // idempotent

        Transaction a = reload(ok);
        assertEquals(CategoryStatus.CATEGORIZED, a.getCategoryStatus());
        assertEquals(0, a.getCategorizerVersion());
        Transaction b = reload(other);
        assertEquals(CategoryStatus.PENDING, b.getCategoryStatus());
        assertEquals(0, b.getRetryCount());
        assertEquals(CategoryStatus.UNCATEGORIZABLE, reload(exhausted).getCategoryStatus());

        // the requeued row is then picked up by the worker
        assertEquals(1, worker.runNow().claimed());
    }

    private Transaction legacy(String category, int retries) {
        return transactions.save(Transaction.builder()
                .amount(new BigDecimal("-2")).currency("BGN").transactionDate(LocalDateTime.now())
                .description("ПОС 1.00 BGN авт.код:123456-OLD SHOP/SOFIA/PAN:5169****9015/CT:08")
                .category(category).retryCount(retries).nextRetryAt(LocalDateTime.now().plusDays(1))
                .bankAccount(account).build());
    }

    @Test
    void manyRowsAreProcessedInSeveralClaimBatchesOfTheConfiguredSize() {
        for (int i = 0; i < 5; i++) {
            pending("BILLA " + i, "-1");
        }

        CategorizationWorker.RunStats stats = newWorker(3, 2).runNow();

        assertEquals(5, stats.claimed());
        assertEquals(5, stats.categorized());
    }
}
