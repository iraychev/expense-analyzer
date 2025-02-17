package com.iraychev.expenseanalyzer.dto;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.iraychev.expenseanalyzer.domain.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankConnectionDto {
    private String reference;
    private String institutionId;
    private String institutionName;
    private String requisitionId;

    @JsonManagedReference
    private List<BankAccountDto> accounts = new ArrayList<>();

    @JsonBackReference
    private User user;
}