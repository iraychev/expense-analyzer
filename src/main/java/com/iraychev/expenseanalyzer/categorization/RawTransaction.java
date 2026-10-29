package com.iraychev.expenseanalyzer.categorization;

import java.math.BigDecimal;
import java.util.List;

/**
 * Everything the parsers need from a stored/fetched bank transaction.
 *
 * @param institutionId    GoCardless institution id of the connection (may be null)
 * @param amount           signed amount (negative = money out)
 * @param description      remittance information
 * @param counterpartyName the other party (creditor for outgoing, debtor for incoming), may be null
 * @param bankTypeCode     bank specific transaction type code (proprietary code, else bank transaction code)
 * @param holderNames      names of the account holder to scrub before anything is sent to the AI
 */
public record RawTransaction(String institutionId,
                             BigDecimal amount,
                             String description,
                             String counterpartyName,
                             String bankTypeCode,
                             List<String> holderNames) {

    public boolean isOutgoing() {
        return amount != null && amount.signum() <= 0;
    }
}
