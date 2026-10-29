package com.iraychev.expenseanalyzer.categorization.parser;

import com.iraychev.expenseanalyzer.categorization.ParsedTransaction;
import com.iraychev.expenseanalyzer.categorization.RawTransaction;

/** Turns a bank specific transaction into a merchant key, direction, type code and redacted AI text. */
public interface TransactionParser {

    /** Whether this parser understands the transaction (by institution id and/or content). */
    boolean supports(RawTransaction raw);

    ParsedTransaction parse(RawTransaction raw);
}
