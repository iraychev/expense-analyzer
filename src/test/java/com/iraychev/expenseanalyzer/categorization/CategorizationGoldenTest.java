package com.iraychev.expenseanalyzer.categorization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iraychev.expenseanalyzer.categorization.ai.AiCategorizer;
import com.iraychev.expenseanalyzer.categorization.ai.AiItem;
import com.iraychev.expenseanalyzer.categorization.parser.DskParser;
import com.iraychev.expenseanalyzer.categorization.parser.RevolutParser;
import com.iraychev.expenseanalyzer.categorization.parser.TransactionParsers;
import com.iraychev.expenseanalyzer.categorization.parser.UniCreditParser;
import com.iraychev.expenseanalyzer.domain.enums.CategorySource;
import com.iraychev.expenseanalyzer.domain.enums.CategoryStatus;
import com.iraychev.expenseanalyzer.dto.BankAccountDto;
import com.iraychev.expenseanalyzer.dto.TransactionDto;
import com.iraychev.expenseanalyzer.service.GoCardlessIntegrationService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Runs the three real bank samples (src/main/resources/cached_transactions_*.json) through parser, rules and the
 * pipeline with a stubbed AI, and compares a hand-labelled sample. A readable report is written to
 * target/golden-report.txt (UTF-8) for inspection.
 */
class CategorizationGoldenTest {

    private static final List<String> HOLDER = List.of("Ivaylo Raychev", "ИВАЙЛО МАРТИНОВ РАЙЧЕВ");

    /** Everything the AI stub was asked about, so tests can check what leaves the system. */
    private static final List<AiItem> aiAsked = new ArrayList<>();

    private static final AiCategorizer STUB_AI = items -> {
        aiAsked.addAll(items);
        return items.stream().collect(Collectors.toMap(AiItem::id, i -> "Other"));
    };

    private record Case(String file, String institution, String iban) {
    }

    private static final Case UNICREDIT = new Case("unicredit", "UNICREDIT_UNCRBGSF", "BG16UNCR70001598168484");
    private static final Case REVOLUT = new Case("revolut", "REVOLUT_REVOGB21", "LT143250059635469546");
    private static final Case DSK = new Case("dsk", "DSK_STSABGSF", "BG68STSA93000027489927");

    private static TransactionParsers parsers;
    private static CategorizationPipeline pipeline;
    private static final Map<String, List<Result>> results = new java.util.LinkedHashMap<>();

    private record Result(TransactionDto tx, ParsedTransaction parsed, CategorizationOutcome outcome) {
    }

    @BeforeAll
    static void run() throws IOException {
        parsers = new TransactionParsers(new UniCreditParser(), new RevolutParser(), new DskParser());

        MerchantCacheLayer emptyCache = mock(MerchantCacheLayer.class);
        when(emptyCache.resolve(anyList())).thenReturn(Map.of());
        pipeline = new CategorizationPipeline(mock(UserOverrideLayer.class), emptyCache, new RuleLayer(), STUB_AI, 25);

        GoCardlessIntegrationService gocardless = new GoCardlessIntegrationService(null, new ObjectMapper());
        StringBuilder report = new StringBuilder();
        for (Case c : List.of(UNICREDIT, REVOLUT, DSK)) {
            BankAccountDto account = BankAccountDto.builder().id(1L).iban(c.iban()).accountId("a").build();
            List<TransactionDto> txs = gocardless.getCachedTransactions(List.of(account)).get(0).getTransactions();
            List<ParsedTransaction> parsed = txs.stream().map(tx -> parsers.parse(new RawTransaction(
                    c.institution(), tx.getAmount(), tx.getDescription(), tx.getCounterpartyName(),
                    tx.getBankTypeCode(), HOLDER))).toList();
            List<CategorizationOutcome> outcomes = pipeline.categorize(
                    parsed.stream().map(p -> new CategorizationRequest(1L, p)).toList());
            List<Result> list = new ArrayList<>();
            for (int i = 0; i < txs.size(); i++) {
                list.add(new Result(txs.get(i), parsed.get(i), outcomes.get(i)));
                Result r = list.get(i);
                report.append(c.file()).append(" | ").append(r.tx().getAmount()).append(" | ")
                        .append(r.parsed().direction()).append(" | ").append(r.tx().getBankTypeCode()).append(" | ")
                        .append(r.parsed().merchantKey()).append(" | ").append(r.outcome().category()).append(" | ")
                        .append(r.outcome().source()).append('\n');
            }
            results.put(c.file(), list);
        }
        Path out = Path.of("target", "golden-report.txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report.toString(), StandardCharsets.UTF_8);
    }

    private static Result find(String file, String descriptionContains) {
        List<Result> matches = results.get(file).stream()
                .filter(r -> r.tx().getDescription() != null && r.tx().getDescription().contains(descriptionContains))
                .toList();
        assertFalse(matches.isEmpty(), "no sample row containing: " + descriptionContains);
        return matches.get(0);
    }

    private static void expect(Result r, String merchantKey, TransactionDirection direction, String category,
                               CategorySource source) {
        assertEquals(merchantKey, r.parsed().merchantKey(), "merchant of " + r.tx().getDescription());
        assertEquals(direction, r.parsed().direction(), "direction of " + r.tx().getDescription());
        assertEquals(category, r.outcome().category(), "category of " + r.tx().getDescription());
        assertEquals(source, r.outcome().source(), "source of " + r.tx().getDescription());
    }

    // ---- hand labelled sample: UniCredit -------------------------------------------------------------------

    @Test
    void unicreditCardMerchantsKeepCyrillicLatinAndDigitsAndAreConsistent() {
        expect(find("unicredit", "SOF.RAZSAD.1130"), "sof razsad 1130", TransactionDirection.EXPENSE, "Other", CategorySource.AI);
        expect(find("unicredit", "BILLA 198 04"), "billa 198 04", TransactionDirection.EXPENSE, "Supermarkets", CategorySource.RULE);
        expect(find("unicredit", "LIDL BALGARIYA"), "lidl balgariya eood", TransactionDirection.EXPENSE, "Supermarkets", CategorySource.RULE);
        expect(find("unicredit", "HK BAR GYUESHEVO"), "hk bar gyueshevo", TransactionDirection.EXPENSE, "Restaurants and bars", CategorySource.RULE);
        expect(find("unicredit", "HK GYUESHEVO K2"), "hk gyueshevo k2", TransactionDirection.EXPENSE, "Other", CategorySource.AI);
        expect(find("unicredit", "DOMINOS.BG"), "dominos bg", TransactionDirection.EXPENSE, "Restaurants and bars", CategorySource.RULE);
        expect(find("unicredit", "STEAM GAMES"), "paypal steam games", TransactionDirection.EXPENSE, "Entertainment and Sport", CategorySource.RULE);
        expect(find("unicredit", "CityGate Sofia Transit"), "citygate sofia transit", TransactionDirection.EXPENSE, "Traveling and Vacation", CategorySource.RULE);
        expect(find("unicredit", "WEBPORTAL.SOFIATR"), "webportal sofiatr", TransactionDirection.EXPENSE, "Other", CategorySource.AI);
        expect(find("unicredit", "Urban Legend"), "urban legend", TransactionDirection.EXPENSE, "Other", CategorySource.AI);
    }

    @Test
    void unicreditNonCardRowsAreResolvedByTypeCode() {
        expect(find("unicredit", "Revolut**8270*"), "revolut", TransactionDirection.TRANSFER, "Transfer out", CategorySource.RULE);
        expect(find("unicredit", "Хонорар /OPNAT/0,Получен"), "уникредит булбанк ад", TransactionDirection.TRANSFER, "Salary", CategorySource.RULE);
        expect(find("unicredit", "Payroll"), "уникредит булбанк ад", TransactionDirection.INCOME, "Salary", CategorySource.RULE);
        expect(find("unicredit", "Застрахователна премия"), "уникредит булбанк ад", TransactionDirection.EXPENSE, "Financial Services", CategorySource.RULE);
        expect(find("unicredit", "колективни инвестиционни"), null, TransactionDirection.EXPENSE, "Financial Services", CategorySource.RULE);
        expect(find("unicredit", "BLINK Такса"), null, TransactionDirection.EXPENSE, "Fees", CategorySource.RULE);
        expect(find("unicredit", "P2P +359 88 840 8422"), "martin raychev", TransactionDirection.TRANSFER, "Transfer out", CategorySource.RULE);
        expect(find("unicredit", "Получен междубанков превод"), "мартин тошков райчев", TransactionDirection.TRANSFER, "Transfer in", CategorySource.RULE);
        expect(find("unicredit", "Издаден вътр.банков превод"), "ангел николаев ботев", TransactionDirection.TRANSFER, "Transfer out", CategorySource.RULE);
    }

    // ---- hand labelled sample: Revolut and DSK ---------------------------------------------------------------

    @Test
    void revolutUsesCounterpartyNameAndProprietaryCode() {
        expect(find("revolut", "Lfs Eood"), "lfs eood", TransactionDirection.EXPENSE, "Other", CategorySource.AI);
        expect(find("revolut", "Top-Up"), null, TransactionDirection.TRANSFER, "Transfer in", CategorySource.RULE);
        expect(find("revolut", "To TSVETOMIR YORDANOV VALCHEV"), "tsvetomir yordanov valchev", TransactionDirection.TRANSFER, "Transfer out", CategorySource.RULE);
        expect(find("revolut", "From ALEKSANDAR RUMENOV MAZNEV"), "aleksandar rumenov maznev", TransactionDirection.TRANSFER, "Transfer in", CategorySource.RULE);
        expect(find("revolut", "ozone.bg"), "ozone bg", TransactionDirection.EXPENSE, "Shopping", CategorySource.RULE);
    }

    @Test
    void dskFeesAndIncomingTransfer() {
        expect(find("dsk", "АВТ.ТАКСА"), null, TransactionDirection.EXPENSE, "Fees", CategorySource.RULE);
        expect(find("dsk", "prevod"), "ивайло мартинов райчев", TransactionDirection.INCOME, "Transfer in", CategorySource.RULE);
    }

    // ---- coverage and invariants -----------------------------------------------------------------------------

    @Test
    void rulesCoverMostOfTheSampleAndEveryRowHasAnOutcome() {
        for (Map.Entry<String, List<Result>> entry : results.entrySet()) {
            List<Result> list = entry.getValue();
            long byRule = list.stream().filter(r -> r.outcome().source() == CategorySource.RULE).count();
            double coverage = (double) byRule / list.size();
            System.out.printf("rule coverage %s: %d/%d (%.0f%%)%n", entry.getKey(), byRule, list.size(), coverage * 100);
            list.forEach(r -> assertNotNull(r.outcome().category()));
            list.forEach(r -> assertTrue(r.outcome().status() == CategoryStatus.CATEGORIZED
                    || r.outcome().status() == CategoryStatus.UNCATEGORIZABLE));
        }
        // Every non-card row of every bank is deterministic; most card rows too
        assertTrue(coverageOf("unicredit") >= 0.65, "unicredit rule coverage " + coverageOf("unicredit"));
        assertTrue(coverageOf("revolut") >= 0.60, "revolut rule coverage " + coverageOf("revolut"));
        assertEquals(1.0, coverageOf("dsk"), 0.0001);
    }

    private static double coverageOf(String file) {
        List<Result> list = results.get(file);
        return (double) list.stream().filter(r -> r.outcome().source() == CategorySource.RULE).count() / list.size();
    }

    @Test
    void aiIsAskedOncePerDistinctMerchantAndNeverSeesSensitiveData() {
        for (AiItem item : aiAsked) {
            assertFalse(item.text().matches("(?s).*\\d{4}\\*{2,}\\d{2,4}.*"), "masked PAN leaked: " + item.text());
            assertFalse(item.text().contains("PAN:"), item.text());
            assertFalse(item.text().contains("TID:"), item.text());
            assertFalse(item.text().toLowerCase().contains("raychev"), item.text());
            assertFalse(item.text().toLowerCase().contains("райчев"), item.text());
            assertFalse(item.text().matches("(?s).*[A-Z]{2}\\d{2}[A-Z0-9]{10,}.*"), "iban leaked: " + item.text());
        }
        assertFalse(aiAsked.isEmpty());
        long unicreditMerchants = results.get("unicredit").stream()
                .filter(r -> r.outcome().source() == CategorySource.AI)
                .map(r -> r.parsed().cacheKey()).distinct().count();
        long unicreditAiRows = results.get("unicredit").stream()
                .filter(r -> r.outcome().source() == CategorySource.AI).count();
        assertTrue(unicreditMerchants < unicreditAiRows, "merchants must be grouped before the AI call");
    }
}
