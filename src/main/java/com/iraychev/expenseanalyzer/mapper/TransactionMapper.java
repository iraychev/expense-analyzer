package com.iraychev.expenseanalyzer.mapper;

import com.iraychev.expenseanalyzer.dto.TransactionDto;
import com.iraychev.expenseanalyzer.domain.entity.Transaction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TransactionMapper {
    TransactionDto toDTO(Transaction transaction);

    @Mapping(target = "id", ignore = true)
    Transaction toEntity(TransactionDto transactionDTO);
}
