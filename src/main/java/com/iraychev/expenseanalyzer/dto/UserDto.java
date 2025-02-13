package com.iraychev.expenseanalyzer.dto;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDto {
    private String email;
    private String name;
    private List<BankConnectionDto> bankConnections;
}