package com.iraychev.expenseanalyzer.categorization.parser;

import com.iraychev.expenseanalyzer.categorization.ParsedTransaction;
import com.iraychev.expenseanalyzer.categorization.RawTransaction;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * DSK Bank: no type codes and the creditor name is padded with blanks (and empty for bank fees), so the merchant is
 * the trimmed counterparty when there is one; the direction follows the sign of the amount.
 */
@Component
@Order(30)
public class DskParser extends GenericParser {

    @Override
    public boolean supports(RawTransaction raw) {
        String institution = raw.institutionId() == null ? "" : raw.institutionId().toUpperCase(Locale.ROOT);
        return institution.contains("DSK") || institution.contains("STSA");
    }

    @Override
    public ParsedTransaction parse(RawTransaction raw) {
        String counterparty = TextUtil.clean(raw.counterpartyName());
        return build(raw, TextUtil.merchantKey(counterparty), byAmountSign(raw), counterparty, false);
    }
}
