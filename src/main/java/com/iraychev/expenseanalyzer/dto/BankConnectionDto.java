package com.iraychev.expenseanalyzer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankConnectionDto {
    private Long id;
    private String reference;
    private String institutionId;
    private String institutionName;
    private String requisitionId;

    private List<BankAccountDto> accounts = new ArrayList<>();

    private Long userId;

    /** Most recent time any account of this connection was fetched from the bank. */
    private Instant syncedAt;

    /** Only set on sync responses: true when no remote fetch was made because of the throttle. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean throttled;

    /** Only set on throttled sync responses: when the next remote fetch is allowed. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Instant nextSyncAvailableAt;
}