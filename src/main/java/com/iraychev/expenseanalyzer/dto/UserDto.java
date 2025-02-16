package com.iraychev.expenseanalyzer.dto;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDto {
    private String email;
    private String name;

    @JsonManagedReference
    private List<BankConnectionDto> bankConnections;
}