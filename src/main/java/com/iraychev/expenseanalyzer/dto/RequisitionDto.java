package com.iraychev.expenseanalyzer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequisitionDto {
    private String id;
    private String created;
    private String redirect;
    private String status;

    @JsonProperty("institution_id")
    private String institutionId;
    private String agreement;
    private String reference;
    private List<String> accounts;

    @JsonProperty("user_language")
    private String userLanguage;
    private String link;
    private String ssn;

    @JsonProperty("account_selection")
    private Boolean accountSelection;

    @JsonProperty("redirect_immediate")
    private Boolean redirectImmediate;
}