package com.iraychev.expenseanalyzer.mapper;

import com.iraychev.expenseanalyzer.dto.TransactionDto;
import com.iraychev.expenseanalyzer.domain.entity.Transaction;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TransactionMapper {
    TransactionDto toDTO(Transaction transaction);
    Transaction toEntity(TransactionDto transactionDTO);
}
