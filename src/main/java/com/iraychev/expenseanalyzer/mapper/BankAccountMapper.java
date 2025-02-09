package com.iraychev.expenseanalyzer.mapper;

import com.iraychev.expenseanalyzer.dto.BankAccountDTO;
import com.iraychev.expenseanalyzer.entity.BankAccount;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface BankAccountMapper {
    BankAccountDTO toDTO(BankAccount bankAccount);
    BankAccount toEntity(BankAccountDTO dto);
}