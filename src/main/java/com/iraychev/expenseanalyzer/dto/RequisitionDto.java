package com.iraychev.expenseanalyzer.dto;

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
    private String redirectUrl;
    private String status;
    private String reference;
    private List<String> accounts;  // External account IDs returned from GoCardless
    private String link;            // The URL the end‑user must visit to authenticate
}
