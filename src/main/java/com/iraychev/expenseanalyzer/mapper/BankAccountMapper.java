package com.iraychev.expenseanalyzer.mapper;

import com.iraychev.expenseanalyzer.dto.BankAccountDto;
import com.iraychev.expenseanalyzer.domain.entity.BankAccount;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface BankAccountMapper {
    BankAccountDto toDTO(BankAccount bankAccount);
    BankAccount toEntity(BankAccountDto dto);
}