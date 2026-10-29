package com.iraychev.expenseanalyzer.categorization;

import com.iraychev.expenseanalyzer.domain.enums.CategorySource;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Layer 3: deterministic rules. First the bank type codes and transaction direction (transfers, fees, salary,
 * investment funds, insurance), then a small list of unambiguous merchant keywords. Everything else is left for
 * the AI. Changing anything here must bump {@link CategorizationPipeline#VERSION}.
 */
@Component
public class RuleLayer implements CategorizationLayer {

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;

    private static final Pattern SALARY = p("заплата|заплащане|хонорар|payroll|salary|wages?");
    private static final Pattern FEE = p("такса|комисион|\\bfees?\\b|\\bcommission\\b");
    private static final Pattern INSURANCE = p("застрахов|insurance");
    private static final Pattern TRANSFER_WORDS = p("превод|\\bprevod\\b|\\btransfer\\b|\\bp2p\\b");

    private static final List<KeywordRule> KEYWORDS = List.of(
            new KeywordRule(p("\\b(billa|lidl|kaufland|fantastico|carrefour|metro|t market|тмаркет|ccc? market)\\b"),
                    Category.SUPERMARKETS),
            new KeywordRule(p("\\b(kfc|mcdonald'?s|mcdonalds|burger king|dominos?|pizza|starbucks|subway|tortilla|"
                    + "gyro|restaurant|ресторант|кафе|cafe|coffee|bar|pub)\\b"), Category.RESTAURANTS),
            new KeywordRule(p("\\b(steam|blizzard|playstation|xbox|netflix|spotify|cinema|кино|gym|fitness)\\b"),
                    Category.ENTERTAINMENT),
            new KeywordRule(p("\\b(aliexpress|emag|ozone|amazon|ikea|zara|h&m)\\b"), Category.SHOPPING),
            new KeywordRule(p("\\b(pharmacy|аптека|sopharmacy|subra|dm|douglas)\\b"), Category.HEALTH),
            new KeywordRule(p("\\b(transit|metropolitan|метрополитен|uber|bolt|taxi|такси|booking|airbnb|ryanair|wizz|"
                    + "wizzair)\\b"), Category.TRAVEL)
    );

    private static Pattern p(String regex) {
        return Pattern.compile(regex, FLAGS);
    }

    private record KeywordRule(Pattern pattern, Category category) {
    }

    @Override
    public Map<Integer, Decision> resolve(List<CategorizationRequest> requests) {
        Map<Integer, Decision> result = new HashMap<>();
        for (int i = 0; i < requests.size(); i++) {
            int index = i;
            classify(requests.get(i).parsed())
                    .ifPresent(category -> result.put(index, new Decision(category, CategorySource.RULE)));
        }
        return result;
    }

    /** Pure function of the parsed transaction; public for the golden test. */
    public Optional<Category> classify(ParsedTransaction p) {
        String text = p.ruleText() == null ? "" : p.ruleText().toLowerCase(Locale.ROOT);
        String code = p.bankTypeCode() == null ? "" : p.bankTypeCode().toUpperCase(Locale.ROOT);
        boolean incoming = p.amount() != null && p.amount().signum() > 0;
        boolean card = code.equals("AC1") || code.equals("CARD_PAYMENT");

        // Bank type codes that are unambiguous
        if (code.equals("C24")) {
            return Optional.of(Category.FEES);
        }
        if (code.equals("FMC")) {
            return Optional.of(Category.FINANCIAL_SERVICES);
        }
        if (code.equals("TFP") || (incoming && !card && SALARY.matcher(text).find())) {
            return Optional.of(Category.SALARY);
        }
        if (!card && INSURANCE.matcher(text).find()) {
            return Optional.of(Category.FINANCIAL_SERVICES);
        }
        if (!card && !incoming && FEE.matcher(text).find()) {
            return Optional.of(Category.FEES);
        }
        if (!card && TRANSFER_WORDS.matcher(text).find()) {
            return Optional.of(incoming ? Category.TRANSFER_IN : Category.TRANSFER_OUT);
        }
        if (p.direction() == TransactionDirection.TRANSFER) {
            return Optional.of(incoming ? Category.TRANSFER_IN : Category.TRANSFER_OUT);
        }

        // Merchant keywords (expenses only: a refund from a shop is not necessarily the same category)
        if (!incoming) {
            for (KeywordRule rule : KEYWORDS) {
                if (rule.pattern().matcher(text).find()) {
                    return Optional.of(rule.category());
                }
            }
        }
        return Optional.empty();
    }
}
