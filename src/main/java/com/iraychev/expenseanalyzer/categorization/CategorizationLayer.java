package com.iraychev.expenseanalyzer.categorization;

import java.util.List;
import java.util.Map;

/** One cheap, non-AI step of the pipeline. Layers are batch oriented so they can use a single query. */
public interface CategorizationLayer {

    /**
     * @return decisions keyed by the index of the request in {@code requests}; requests the layer cannot decide
     * are simply absent
     */
    Map<Integer, Decision> resolve(List<CategorizationRequest> requests);
}
