package com.iraychev.expenseanalyzer.categorization;

import com.iraychev.expenseanalyzer.categorization.ai.AiCategorizer;
import com.iraychev.expenseanalyzer.categorization.ai.AiItem;
import com.iraychev.expenseanalyzer.categorization.parser.DskParser;
import com.iraychev.expenseanalyzer.categorization.parser.RevolutParser;
import com.iraychev.expenseanalyzer.categorization.parser.TransactionParsers;
import com.iraychev.expenseanalyzer.categorization.parser.UniCreditParser;
import com.iraychev.expenseanalyzer.domain.entity.BankAccount;
import com.iraychev.expenseanalyzer.domain.entity.BankConnection;
import com.iraychev.expenseanalyzer.domain.entity.Transaction;
import com.iraychev.expenseanalyzer.domain.entity.User;
import com.iraychev.expenseanalyzer.domain.enums.CategorySource;
import com.iraychev.expenseanalyzer.domain.enums.CategoryStatus;
import com.iraychev.expenseanalyzer.dto.CategoryUpdateRequest;
import com.iraychev.expenseanalyzer.dto.TransactionDto;
import com.iraychev.expenseanalyzer.exception.ResourceNotFoundException;
import com.iraychev.expenseanalyzer.exception.ValidationException;
import com.iraychev.expenseanalyzer.mapper.TransactionMapper;
import com.iraychev.expenseanalyzer.repository.BankAccountRepository;
import com.iraychev.expenseanalyzer.repository.BankConnectionRepository;
import com.iraychev.expenseanalyzer.repository.TransactionRepository;
import com.iraychev.expenseanalyzer.repository.UserCategoryRuleRepository;
import com.iraychev.expenseanalyzer.repository.UserRepository;
import com.iraychev.expenseanalyzer.service.TransactionCategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** User overrides (PUT category) and the nightly maintenance job against a real JPA schema. */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:workerdb;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS expense_analyzer",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = CategorizationWorkerJpaTest.JpaTestConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CategoryOverrideJpaTest {

    @Autowired private TransactionRepository transactions;
    @Autowired private UserRepository users;
    @Autowired private BankConnectionRepository connections;
    @Autowired private BankAccountRepository accounts;
    @Autowired private UserCategoryRuleRepository rules;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private JdbcTemplate jdbc;

    private final TransactionParsers parsers = new TransactionParsers(new UniCreditParser(), new RevolutParser(), new DskParser());
    private final List<AiItem> aiAsked = new ArrayList<>();
    private final Map<String, String> aiAnswers = new HashMap<>();

    private TransactionCategoryService service;
    private User alice;
    private User bob;
    private BankAccount aliceAccount;
    private BankAccount bobAccount;

    @BeforeEach
    void setUp() {
        jdbc.update("delete from expense_analyzer.transactions");
        jdbc.update("delete from expense_analyzer.user_category_rules");
        accounts.deleteAll();
        connections.deleteAll();
        users.deleteAll();
        aiAsked.clear();
        aiAnswers.clear();

        alice = newUser("alice");
        bob = newUser("bob");
        aliceAccount = newAccount(alice);
        bobAccount = newAccount(bob);
        service = new TransactionCategoryService(transactions, rules, users, parsers, Mappers.getMapper(TransactionMapper.class));
    }

    private User newUser(String name) {
        return users.save(User.builder().name(name + " Smith").username(name).password("x").email(name + "@x.com").build());
    }

    private BankAccount newAccount(User user) {
        BankConnection c = connections.save(BankConnection.builder().reference("r").institutionId("UNICREDIT_UNCRBGSF")
                .requisitionId("req-" + user.getUsername()).user(user).build());
        return accounts.save(BankAccount.builder().accountId("a").iban("BG").bankConnection(c).build());
    }

    private Transaction tx(BankAccount account, String merchant, CategoryStatus status, String category,
                           CategorySource source, Integer version) {
        return transactions.save(Transaction.builder().amount(new BigDecimal("-3")).currency("BGN")
                .transactionDate(LocalDateTime.now()).valueDate(LocalDateTime.now())
                .description("ПОС 3.00 BGN авт.код:123456-" + merchant + "/SOFIA/PAN:5169****9015/CT:08,Операция с карта")
                .bankTypeCode("AC1").category(category).categoryStatus(status).categorySource(source)
                .categorizerVersion(version).retryCount(0).bankAccount(account).build());
    }

    private Transaction reload(Transaction t) {
        return transactions.findById(t.getId()).orElseThrow();
    }

    /** The real service is @Transactional through its Spring proxy; here it is called inside a transaction. */
    private TransactionDto update(String username, Long id, CategoryUpdateRequest request) {
        return new org.springframework.transaction.support.TransactionTemplate(txManager)
                .execute(status -> service.updateCategory(username, id, request));
    }

    private static CategoryUpdateRequest request(String category, boolean applyToMerchant) {
        return new CategoryUpdateRequest(category, applyToMerchant);
    }

    // ---- PUT /transactions/{id}/category -----------------------------------------------------------------

    @Test
    void changesOnlyTheTransactionWhenNotAppliedToTheMerchant() {
        Transaction a = tx(aliceAccount, "OLDES", CategoryStatus.CATEGORIZED, "Shopping", CategorySource.AI, 1);
        Transaction b = tx(aliceAccount, "OLDES", CategoryStatus.CATEGORIZED, "Shopping", CategorySource.AI, 1);

        TransactionDto dto = update("alice", a.getId(), request("Restaurants and bars", false));

        assertEquals("Restaurants and bars", dto.getCategory());
        assertEquals(CategorySource.USER, dto.getCategorySource());
        assertEquals(CategoryStatus.CATEGORIZED, dto.getCategoryStatus());
        assertEquals("oldes", dto.getMerchant());
        assertEquals("Shopping", reload(b).getCategory());
        assertEquals(0, rules.count());
    }

    @Test
    void applyToMerchantStoresARuleAndUpdatesTheUsersOtherTransactionsOnly() {
        Transaction a = tx(aliceAccount, "OLDES", CategoryStatus.CATEGORIZED, "Shopping", CategorySource.AI, 1);
        Transaction pendingRow = tx(aliceAccount, "OLDES", CategoryStatus.PENDING, "Other", null, null);
        Transaction ownChoice = tx(aliceAccount, "OLDES", CategoryStatus.CATEGORIZED, "Fees", CategorySource.USER, 1);
        Transaction other = tx(aliceAccount, "LIDL 1", CategoryStatus.CATEGORIZED, "Shopping", CategorySource.AI, 1);
        Transaction bobs = tx(bobAccount, "OLDES", CategoryStatus.CATEGORIZED, "Shopping", CategorySource.AI, 1);

        update("alice", a.getId(), request("Restaurants and bars", true));

        assertEquals("Restaurants and bars", reload(pendingRow).getCategory());
        assertEquals(CategorySource.USER, reload(pendingRow).getCategorySource());
        assertEquals(CategoryStatus.CATEGORIZED, reload(pendingRow).getCategoryStatus());
        assertEquals("Fees", reload(ownChoice).getCategory(), "an earlier individual choice is kept");
        assertEquals("Shopping", reload(other).getCategory());
        assertEquals("Shopping", reload(bobs).getCategory(), "other users are untouched");
        assertEquals(1, rules.count());
        assertEquals("Restaurants and bars", rules.findByUserIdAndMerchant(alice.getId(), "oldes").orElseThrow().getCategory());
    }

    @Test
    void changingTheRuleAgainUpdatesInsteadOfDuplicating() {
        Transaction a = tx(aliceAccount, "OLDES", CategoryStatus.CATEGORIZED, "Shopping", CategorySource.AI, 1);

        update("alice", a.getId(), request("Restaurants and bars", true));
        update("alice", a.getId(), request("Entertainment and Sport", true));

        assertEquals(1, rules.count());
        assertEquals("Entertainment and Sport", reload(a).getCategory());
    }

    @Test
    void anotherUsersTransactionIsNotFound() {
        Transaction bobsTx = tx(bobAccount, "OLDES", CategoryStatus.CATEGORIZED, "Shopping", CategorySource.AI, 1);

        assertThrows(ResourceNotFoundException.class,
                () -> update("alice", bobsTx.getId(), request("Fees", false)));
        assertThrows(ResourceNotFoundException.class,
                () -> update("alice", 999_999L, request("Fees", false)));
        assertEquals("Shopping", reload(bobsTx).getCategory());
    }

    @Test
    void unknownCategoryIsRejected() {
        Transaction a = tx(aliceAccount, "OLDES", CategoryStatus.CATEGORIZED, "Shopping", CategorySource.AI, 1);
        ValidationException e = assertThrows(ValidationException.class,
                () -> update("alice", a.getId(), request("Nonsense", false)));
        assertEquals("category", e.getField());
    }

    @Test
    void theRuleIsLayerOneForFutureTransactionsAndNoAiIsCalled() {
        Transaction a = tx(aliceAccount, "OLDES", CategoryStatus.CATEGORIZED, "Shopping", CategorySource.AI, 1);
        update("alice", a.getId(), request("Restaurants and bars", true));
        Transaction later = tx(aliceAccount, "OLDES", CategoryStatus.PENDING, "Other", null, null);
        Transaction bobsLater = tx(bobAccount, "OLDES", CategoryStatus.PENDING, "Other", null, null);
        aiAnswers.put("Card payment at: OLDES", "Health and Beauty");

        newWorker().runNow();

        assertEquals("Restaurants and bars", reload(later).getCategory());
        assertEquals(CategorySource.USER, reload(later).getCategorySource());
        assertEquals("Health and Beauty", reload(bobsLater).getCategory(), "bob has no rule and gets the AI answer");
        assertEquals(1, aiAsked.size());
    }

    private CategorizationWorker newWorker() {
        AiCategorizer ai = items -> {
            aiAsked.addAll(items);
            Map<String, String> out = new HashMap<>();
            items.forEach(i -> out.put(i.id(), aiAnswers.getOrDefault(i.text(), AiCategorizer.UNKNOWN)));
            return out;
        };
        MerchantCacheLayer cache = mock(MerchantCacheLayer.class);
        when(cache.resolve(anyList())).thenReturn(Map.of());
        CategorizationPipeline pipeline = new CategorizationPipeline(new UserOverrideLayer(rules), cache, new RuleLayer(), ai, 25);
        return new CategorizationWorker(transactions, parsers, pipeline, txManager, 10, 10, 3,
                Duration.ofMinutes(5), Duration.ofMinutes(10), Duration.ofMinutes(5));
    }

    // ---- nightly job -------------------------------------------------------------------------------------

    private CategorizationMaintenanceJob job() {
        return new CategorizationMaintenanceJob(transactions, mock(CategorizationWorker.class), txManager, 100, 100);
    }

    private void retryAt(Transaction t, LocalDateTime when) {
        jdbc.update("update expense_analyzer.transactions set other_next_retry_at = ? where id = ?", when, t.getId());
    }

    @Test
    void nightlyJobRequeuesDueFailedRowsKeepingTheirFailureCount() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Transaction due = tx(aliceAccount, "A", CategoryStatus.FAILED, "Other", null, 1);
        Transaction notYet = tx(aliceAccount, "B", CategoryStatus.FAILED, "Other", null, 1);
        jdbc.update("update expense_analyzer.transactions set other_retry_count = 5 where id = ?", due.getId());
        retryAt(due, now.minusHours(1));
        retryAt(notYet, now.plusHours(5));

        CategorizationMaintenanceJob.Summary summary = job().runNow();

        assertEquals(1, summary.failedRequeued());
        Transaction r = reload(due);
        assertEquals(CategoryStatus.PENDING, r.getCategoryStatus());
        assertEquals(5, r.getRetryCount());
        assertNull(r.getNextRetryAt());
        assertEquals(CategoryStatus.FAILED, reload(notYet).getCategoryStatus());
    }

    @Test
    void nightlyJobRequeuesDueUncategorizableRowsWithAFreshCount() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Transaction due = tx(aliceAccount, "A", CategoryStatus.UNCATEGORIZABLE, "Other", CategorySource.AI, 1);
        Transaction notYet = tx(aliceAccount, "B", CategoryStatus.UNCATEGORIZABLE, "Other", CategorySource.AI, 1);
        jdbc.update("update expense_analyzer.transactions set other_retry_count = 4 where id = ?", due.getId());
        retryAt(due, now.minusDays(1));
        retryAt(notYet, now.plusDays(10));

        CategorizationMaintenanceJob.Summary summary = job().runNow();

        assertEquals(1, summary.uncategorizableRequeued());
        assertEquals(CategoryStatus.PENDING, reload(due).getCategoryStatus());
        assertEquals(0, reload(due).getRetryCount());
        assertEquals(CategoryStatus.UNCATEGORIZABLE, reload(notYet).getCategoryStatus());
    }

    @Test
    void nightlyJobRequeuesOutdatedVersionsButNeverUserChoices() {
        Transaction old = tx(aliceAccount, "A", CategoryStatus.CATEGORIZED, "Shopping", CategorySource.AI, 0);
        Transaction legacy = tx(aliceAccount, "B", CategoryStatus.CATEGORIZED, "Shopping", null, null);
        Transaction current = tx(aliceAccount, "C", CategoryStatus.CATEGORIZED, "Shopping", CategorySource.AI,
                CategorizationPipeline.VERSION);
        Transaction userChoice = tx(aliceAccount, "D", CategoryStatus.CATEGORIZED, "Fees", CategorySource.USER, 0);

        CategorizationMaintenanceJob.Summary summary = job().runNow();

        assertEquals(2, summary.outdatedRequeued());
        assertEquals(CategoryStatus.PENDING, reload(old).getCategoryStatus());
        assertEquals("Shopping", reload(old).getCategory(), "the old category stays visible meanwhile");
        assertEquals(CategoryStatus.PENDING, reload(legacy).getCategoryStatus());
        assertEquals(CategoryStatus.CATEGORIZED, reload(current).getCategoryStatus());
        assertEquals(CategoryStatus.CATEGORIZED, reload(userChoice).getCategoryStatus());
    }

    @Test
    void nightlyJobReportsCountsByStatusAndSource() {
        tx(aliceAccount, "A", CategoryStatus.CATEGORIZED, "Shopping", CategorySource.AI, CategorizationPipeline.VERSION);
        tx(aliceAccount, "B", CategoryStatus.CATEGORIZED, "Shopping", CategorySource.AI, CategorizationPipeline.VERSION);
        tx(aliceAccount, "C", CategoryStatus.CATEGORIZED, "Fees", CategorySource.RULE, CategorizationPipeline.VERSION);

        CategorizationMaintenanceJob.Summary summary = job().runNow();

        assertEquals(2L, summary.countsByStatusAndSource().get("CATEGORIZED/AI"));
        assertEquals(1L, summary.countsByStatusAndSource().get("CATEGORIZED/RULE"));
        assertNotNull(summary);
        assertTrue(summary.failedRequeued() == 0);
    }
}
