package com.iraychev.expenseanalyzer.categorization.parser;

import com.iraychev.expenseanalyzer.categorization.ParsedTransaction;
import com.iraychev.expenseanalyzer.categorization.RawTransaction;
import com.iraychev.expenseanalyzer.categorization.TransactionDirection;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * UniCredit Bulbank. Card payments look like
 * {@code ПОС 4.10 BGN авт.код:984725-SOF.RAZSAD.1130/RUSE/TID:93925306/PAN:5169****9015/CT:08/NOM::MDU,Операция с карта}:
 * the merchant is the text after {@code авт.код:<digits>-} up to the first "/" (a URL scheme is kept together).
 * Other rows are classified by the bank type code (AC1 card, TF and TB transfers, C24 fees, ...).
 */
@Component
@Order(10)
public class UniCreditParser extends GenericParser {

    private static final Pattern CARD_MERCHANT = Pattern.compile(
            "авт\\.код:\\d+-((?:https?:)?//[^/]+|[^/]*)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern REVOLUT_TOPUP = Pattern.compile("^revolut(\\s+\\d+)?$");

    @Override
    public boolean supports(RawTransaction raw) {
        String institution = raw.institutionId() == null ? "" : raw.institutionId().toUpperCase(Locale.ROOT);
        return institution.contains("UNICREDIT") || institution.contains("UNCR")
                || (raw.description() != null && raw.description().toLowerCase(Locale.ROOT).contains("авт.код:"));
    }

    @Override
    public ParsedTransaction parse(RawTransaction raw) {
        String code = raw.bankTypeCode();
        String description = raw.description() == null ? "" : raw.description();

        Matcher matcher = CARD_MERCHANT.matcher(description);
        if (matcher.find()) {
            String displayMerchant = TextUtil.clean(matcher.group(1));
            String key = TextUtil.merchantKey(displayMerchant);
            // Top-ups of the user's own Revolut card: "Revolut**8270*" - the digits vary per payment.
            if (key != null && REVOLUT_TOPUP.matcher(key).matches()) {
                return build(raw, "revolut", TransactionDirection.TRANSFER, "Revolut", true);
            }
            return build(raw, key, byAmountSign(raw), displayMerchant, true);
        }

        TransactionDirection direction = byTypeCode(code, raw);
        String counterparty = TextUtil.clean(raw.counterpartyName());
        return build(raw, TextUtil.merchantKey(counterparty), direction, counterparty, false);
    }

    private static TransactionDirection byTypeCode(String code, RawTransaction raw) {
        if (code == null) {
            return byAmountSign(raw);
        }
        return switch (code.toUpperCase(Locale.ROOT)) {
            case "TF1", "TF2", "TF3", "TB1", "TB2", "TB4" -> TransactionDirection.TRANSFER;
            case "TFP" -> TransactionDirection.INCOME;
            case "TFA", "FMC", "C24" -> TransactionDirection.EXPENSE;
            default -> byAmountSign(raw);
        };
    }
}
