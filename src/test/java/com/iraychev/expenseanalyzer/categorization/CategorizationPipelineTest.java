package com.iraychev.expenseanalyzer.categorization;

import com.iraychev.expenseanalyzer.categorization.ai.AiCategorizer;
import com.iraychev.expenseanalyzer.categorization.ai.AiItem;
import com.iraychev.expenseanalyzer.categorization.ai.AiUnavailableException;
import com.iraychev.expenseanalyzer.domain.enums.CategorySource;
import com.iraychev.expenseanalyzer.domain.enums.CategoryStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CategorizationPipelineTest {

    private UserOverrideLayer overrides;
    private MerchantCacheLayer cache;
    private final RuleLayer rules = new RuleLayer();
    private final List<List<AiItem>> aiCalls = new ArrayList<>();
    private final Map<String, String> aiAnswersByText = new HashMap<>();
    private AiUnavailableException aiFailure;

    private final AiCategorizer ai = items -> {
        aiCalls.add(items);
        if (aiFailure != null) {
            throw aiFailure;
        }
        Map<String, String> answers = new HashMap<>();
        for (AiItem item : items) {
            answers.put(item.id(), aiAnswersByText.getOrDefault(item.text(), AiCategorizer.UNKNOWN));
        }
        return answers;
    };

    @BeforeEach
    void setUp() {
        overrides = mock(UserOverrideLayer.class);
        cache = mock(MerchantCacheLayer.class);
        when(overrides.resolve(anyList())).thenReturn(Map.of());
        when(cache.resolve(anyList())).thenReturn(Map.of());
    }

    private CategorizationPipeline pipeline(int batchSize) {
        return new CategorizationPipeline(overrides, cache, rules, ai, batchSize);
    }

    private static CategorizationRequest card(String merchantKey, String amount) {
        return new CategorizationRequest(1L, new ParsedTransaction(merchantKey, TransactionDirection.EXPENSE, "AC1",
                new BigDecimal(amount), merchantKey, "Card payment at: " + merchantKey));
    }

    @Test
    void overrideBeatsCacheAndRulesAndLaterLayersOnlySeeWhatIsLeft() {
        List<CategorizationRequest> requests = List.of(card("billa 1", "-5"), card("mystery", "-5"));
        when(overrides.resolve(anyList())).thenReturn(Map.of(0, new Decision(Category.SHOPPING, CategorySource.USER)));
        when(cache.resolve(anyList())).thenAnswer(inv -> {
            List<?> seen = inv.getArgument(0);
            assertEquals(1, seen.size(), "cache must only see the request the override did not decide");
            return Map.of(0, new Decision(Category.TRAVEL, CategorySource.CACHE));
        });

        List<CategorizationOutcome> out = pipeline(25).categorize(requests);

        assertEquals("Shopping", out.get(0).category());
        assertEquals(CategorySource.USER, out.get(0).source());
        assertEquals("Traveling and Vacation", out.get(1).category());
        assertEquals(CategorySource.CACHE, out.get(1).source());
        assertTrue(aiCalls.isEmpty());
    }

    @Test
    void rulesResolveBeforeAnyAiCall() {
        List<CategorizationOutcome> out = pipeline(25).categorize(List.of(card("billa 198 04", "-5")));

        assertEquals("Supermarkets", out.get(0).category());
        assertEquals(CategorySource.RULE, out.get(0).source());
        assertTrue(aiCalls.isEmpty());
        verify(cache, never()).remember(eq("billa 198 04"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void aiIsCalledOncePerDistinctMerchantAndAnswerIsCachedAndFannedOut() {
        aiAnswersByText.put("Card payment at: zlaten klas", "Supermarkets");
        aiAnswersByText.put("Card payment at: oldes", "Restaurants and bars");

        List<CategorizationOutcome> out = pipeline(25).categorize(List.of(
                card("zlaten klas", "-7"), card("oldes", "-14"), card("zlaten klas", "-5.9")));

        assertEquals(1, aiCalls.size());
        assertEquals(2, aiCalls.get(0).size());
        assertEquals("Supermarkets", out.get(0).category());
        assertEquals("Supermarkets", out.get(2).category());
        assertEquals("Restaurants and bars", out.get(1).category());
        assertEquals(CategorySource.AI, out.get(0).source());
        verify(cache).remember("zlaten klas", Category.SUPERMARKETS);
        verify(cache).remember("oldes", Category.RESTAURANTS);
    }

    @Test
    void manyMerchantsAreSplitIntoBatches() {
        List<CategorizationRequest> requests = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            requests.add(card("shop" + i, "-1"));
        }

        pipeline(3).categorize(requests);

        assertEquals(List.of(3, 3, 1), aiCalls.stream().map(List::size).toList());
    }

    @Test
    void modelSayingUnknownIsUncategorizableAndNotCached() {
        List<CategorizationOutcome> out = pipeline(25).categorize(List.of(card("xyz", "-1")));

        assertEquals(CategoryStatus.UNCATEGORIZABLE, out.get(0).status());
        assertEquals("Other", out.get(0).category());
        assertEquals(CategorySource.AI, out.get(0).source());
        verify(cache, never()).remember(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void aiFailureMarksOnlyThoseRowsFailedAndOthersStayResolved() {
        aiFailure = new AiUnavailableException("rate limited", 429);

        List<CategorizationOutcome> out = pipeline(25).categorize(List.of(card("billa 1", "-1"), card("xyz", "-1")));

        assertEquals(CategoryStatus.CATEGORIZED, out.get(0).status());
        assertEquals(CategoryStatus.FAILED, out.get(1).status());
        assertNull(out.get(1).source());
        assertEquals("Other", out.get(1).category());
    }

    @Test
    void answerOfTheWrongKindForTheDirectionIsRejected() {
        aiAnswersByText.put("Card payment at: xyz", "Salary");

        List<CategorizationOutcome> out = pipeline(25).categorize(List.of(card("xyz", "-1")));

        assertEquals(CategoryStatus.UNCATEGORIZABLE, out.get(0).status());
    }

    @Test
    void rowsWithoutMerchantAreSentOncePerDistinctText() {
        ParsedTransaction p = new ParsedTransaction(null, TransactionDirection.INCOME, null, new BigDecimal("10"),
                "gift", "Incoming: gift");
        aiAnswersByText.put("Incoming: gift", "Other income");

        List<CategorizationOutcome> out = pipeline(25).categorize(List.of(
                new CategorizationRequest(1L, p), new CategorizationRequest(1L, p)));

        assertEquals(1, aiCalls.get(0).size());
        assertEquals("Other income", out.get(0).category());
        assertEquals("Other income", out.get(1).category());
        verify(cache, never()).remember(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
