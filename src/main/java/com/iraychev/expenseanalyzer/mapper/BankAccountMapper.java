package com.iraychev.expenseanalyzer.mapper;

import com.iraychev.expenseanalyzer.domain.entity.BankAccount;
import com.iraychev.expenseanalyzer.dto.BankAccountDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = { TransactionMapper.class })
public interface BankAccountMapper {
    BankAccountDto toDTO(BankAccount bankAccount);
    BankAccount toEntity(BankAccountDto dto);
}
