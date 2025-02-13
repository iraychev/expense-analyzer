package com.iraychev.expenseanalyzer.mapper;

import com.iraychev.expenseanalyzer.dto.BankConnectionDto;
import com.iraychev.expenseanalyzer.domain.entity.BankConnection;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = { BankAccountMapper.class })
public interface BankConnectionMapper {
    BankConnectionDto toDTO(BankConnection bankConnection);
    BankConnection toEntity(BankConnectionDto dto);
}