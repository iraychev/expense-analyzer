package com.iraychev.expenseanalyzer.mapper;

import com.iraychev.expenseanalyzer.domain.entity.Transaction;
import com.iraychev.expenseanalyzer.dto.TransactionDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;


@Mapper(componentModel = "spring")
public interface TransactionMapper {
    @Mapping(target = "bankAccountId", source = "bankAccount.id")
    TransactionDto toDTO(Transaction transaction);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "bankAccount", ignore = true)
    Transaction toEntity(TransactionDto transactionDTO);
}