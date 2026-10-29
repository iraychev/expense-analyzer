package com.iraychev.expenseanalyzer.categorization.parser;

import com.iraychev.expenseanalyzer.categorization.ParsedTransaction;
import com.iraychev.expenseanalyzer.categorization.RawTransaction;
import com.iraychev.expenseanalyzer.categorization.TransactionDirection;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * Revolut: {@code creditorName}/{@code debtorName} is the merchant or the other person and
 * {@code proprietaryBankTransactionCode} is CARD_PAYMENT, TRANSFER or TOPUP.
 */
@Component
@Order(20)
public class RevolutParser extends GenericParser {

    private static final Set<String> KNOWN_CODES = Set.of("CARD_PAYMENT", "TRANSFER", "TOPUP", "EXCHANGE", "ATM", "FEE");

    @Override
    public boolean supports(RawTransaction raw) {
        String institution = raw.institutionId() == null ? "" : raw.institutionId().toUpperCase(Locale.ROOT);
        return institution.contains("REVOLUT")
                || (raw.bankTypeCode() != null && KNOWN_CODES.contains(raw.bankTypeCode().toUpperCase(Locale.ROOT))
                && !institution.contains("UNICREDIT") && !institution.contains("DSK"));
    }

    @Override
    public ParsedTransaction parse(RawTransaction raw) {
        String code = raw.bankTypeCode() == null ? "" : raw.bankTypeCode().toUpperCase(Locale.ROOT);
        String counterparty = TextUtil.clean(raw.counterpartyName());
        String key = TextUtil.merchantKey(counterparty);
        return switch (code) {
            case "CARD_PAYMENT" -> build(raw, key, byAmountSign(raw), counterparty, true);
            case "TRANSFER" -> build(raw, key, TransactionDirection.TRANSFER, counterparty, false);
            case "TOPUP" -> build(raw, null, TransactionDirection.TRANSFER, null, false);
            case "FEE" -> build(raw, null, TransactionDirection.EXPENSE, null, false);
            default -> build(raw, key, byAmountSign(raw), counterparty, false);
        };
    }
}
