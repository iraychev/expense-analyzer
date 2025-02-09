package com.iraychev.expenseanalyzer.mapper;

import com.iraychev.expenseanalyzer.dto.TransactionDTO;
import com.iraychev.expenseanalyzer.entity.Transaction;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TransactionMapper {
    TransactionDTO toDTO(Transaction transaction);
    Transaction toEntity(TransactionDTO transactionDTO);
}
