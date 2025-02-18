package com.iraychev.expenseanalyzer.mapper;

import com.iraychev.expenseanalyzer.domain.entity.BankAccount;
import com.iraychev.expenseanalyzer.domain.entity.Transaction;
import com.iraychev.expenseanalyzer.dto.BankAccountDto;
import com.iraychev.expenseanalyzer.dto.TransactionDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", uses = {TransactionMapper.class})
public interface BankAccountMapper {
    @Mapping(target = "bankConnectionId", source = "bankConnection.id")
    @Mapping(target = "transactions", source = "transactions")
    BankAccountDto toDTO(BankAccount bankAccount);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "bankConnection", ignore = true)
    BankAccount toEntity(BankAccountDto dto);
}