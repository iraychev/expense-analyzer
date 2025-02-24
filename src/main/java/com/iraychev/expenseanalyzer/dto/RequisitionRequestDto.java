package com.iraychev.expenseanalyzer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RequisitionRequestDto {
    private String redirect;
    // Relevant ones include REVOLUT_REVOLT21, UNICREDIT_UNCRBGSF, DSKBANK_STSABGSFXXX, FIBANK_FINVBGSF,
    private String institutionId;
    private String reference;
    private String agreement;
    private String userLanguage;
}
