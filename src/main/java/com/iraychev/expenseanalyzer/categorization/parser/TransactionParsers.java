package com.iraychev.expenseanalyzer.categorization.parser;

import com.iraychev.expenseanalyzer.categorization.ParsedTransaction;
import com.iraychev.expenseanalyzer.categorization.RawTransaction;
import org.springframework.stereotype.Component;

import java.util.List;

/** Picks the first bank specific parser that understands a transaction, falling back to the generic one. */
@Component
public class TransactionParsers {

    private final List<TransactionParser> parsers;
    private final TransactionParser fallback = new GenericParser();

    /** Bank specific parsers in priority order. */
    public TransactionParsers(UniCreditParser uniCredit, RevolutParser revolut, DskParser dsk) {
        this.parsers = List.of(uniCredit, revolut, dsk);
    }

    public ParsedTransaction parse(RawTransaction raw) {
        for (TransactionParser parser : parsers) {
            if (parser.supports(raw)) {
                return parser.parse(raw);
            }
        }
        return fallback.parse(raw);
    }
}
