package com.iraychev.expenseanalyzer.categorization.parser;

import com.iraychev.expenseanalyzer.categorization.ParsedTransaction;
import com.iraychev.expenseanalyzer.categorization.RawTransaction;
import com.iraychev.expenseanalyzer.categorization.TransactionDirection;

import java.util.Locale;

/**
 * Fallback parser for banks without a dedicated implementation (and base for the bank specific ones): the merchant
 * is the counterparty name, the direction follows the sign of the amount.
 */
public class GenericParser implements TransactionParser {

    @Override
    public boolean supports(RawTransaction raw) {
        return true;
    }

    @Override
    public ParsedTransaction parse(RawTransaction raw) {
        String merchantKey = TextUtil.merchantKey(raw.counterpartyName());
        TransactionDirection direction = byAmountSign(raw);
        return build(raw, merchantKey, direction, TextUtil.clean(raw.counterpartyName()), false);
    }

    protected static TransactionDirection byAmountSign(RawTransaction raw) {
        return raw.isOutgoing() ? TransactionDirection.EXPENSE : TransactionDirection.INCOME;
    }

    /**
     * @param displayMerchant merchant name as printed by the bank (used in the AI text), may be null
     * @param card            true for card payments: the AI only gets the merchant, never the free text
     */
    protected static ParsedTransaction build(RawTransaction raw, String merchantKey, TransactionDirection direction,
                                             String displayMerchant, boolean card) {
        String redactedDescription = TextUtil.redact(raw.description(), raw.holderNames());
        String aiText;
        if (card && displayMerchant != null) {
            aiText = "Card payment at: " + TextUtil.redact(displayMerchant, raw.holderNames());
        } else {
            StringBuilder sb = new StringBuilder(direction == TransactionDirection.INCOME ? "Incoming: " : "Outgoing: ");
            sb.append(redactedDescription);
            if (displayMerchant != null && direction != TransactionDirection.TRANSFER) {
                sb.append(" (counterparty: ").append(TextUtil.redact(displayMerchant, raw.holderNames())).append(')');
            }
            aiText = sb.toString();
        }
        String ruleText = ((merchantKey == null ? "" : merchantKey + " ") + redactedDescription)
                .toLowerCase(Locale.ROOT);
        return new ParsedTransaction(merchantKey, direction, raw.bankTypeCode(), raw.amount(), ruleText, aiText);
    }
}
