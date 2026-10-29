package com.iraychev.expenseanalyzer.categorization;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Layer 1: per-user merchant to category overrides. Placeholder until user overrides exist. */
@Component
public class UserOverrideLayer implements CategorizationLayer {

    @Override
    public Map<Integer, Decision> resolve(List<CategorizationRequest> requests) {
        return Map.of();
    }
}
