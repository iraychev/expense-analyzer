package com.iraychev.expenseanalyzer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.annotation.Nullable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RequisitionRequestDto {
    private String redirect;
    private String institutionId;
    private String reference;
    
    @Nullable
    private String agreement;
    private String userLanguage;
}
