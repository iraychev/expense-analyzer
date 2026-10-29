package com.iraychev.expenseanalyzer.categorization.ai;

import java.util.List;
import java.util.Map;

/** Batch categorization by a language model. Kept behind an interface so tests can stub it. */
public interface AiCategorizer {

    /** The answer meaning "I cannot tell"; such items become UNCATEGORIZABLE. */
    String UNKNOWN = "UNKNOWN";

    /**
     * @param items distinct merchants/texts, at most a few dozen per call
     * @return answer per {@link AiItem#id()}: a category key or {@link #UNKNOWN}; ids the model skipped are absent
     * @throws AiUnavailableException for transient or technical failures (rate limit, 5xx, network, bad response)
     */
    Map<String, String> categorize(List<AiItem> items) throws AiUnavailableException;
}
