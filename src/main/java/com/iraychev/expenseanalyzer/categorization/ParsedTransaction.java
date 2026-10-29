package com.iraychev.expenseanalyzer.categorization;

import java.math.BigDecimal;

/**
 * Result of parsing a bank transaction.
 *
 * @param merchantKey  canonical lower-case merchant/counterparty key (letters and digits, single spaces); null when
 *                     the transaction has no meaningful merchant (fees, funds, top-ups)
 * @param direction    income / expense / transfer
 * @param bankTypeCode bank specific type code, may be null
 * @param amount       signed amount
 * @param ruleText     lower-case text the keyword rules run on (merchant + redacted description)
 * @param aiText       redacted text that is allowed to leave the system
 */
public record ParsedTransaction(String merchantKey,
                                TransactionDirection direction,
                                String bankTypeCode,
                                BigDecimal amount,
                                String ruleText,
                                String aiText) {

    /** Key under which results are cached: null when there is no merchant; income is cached apart from expenses. */
    public String cacheKey() {
        if (merchantKey == null || merchantKey.isBlank()) {
            return null;
        }
        return direction == TransactionDirection.INCOME ? "income:" + merchantKey : merchantKey;
    }

    public boolean isOutgoing() {
        return amount != null && amount.signum() <= 0;
    }
}
