package com.iraychev.expenseanalyzer.mapper;

import com.iraychev.expenseanalyzer.domain.entity.BankAccount;
import com.iraychev.expenseanalyzer.dto.BankAccountDto;
import com.iraychev.expenseanalyzer.dto.BankConnectionDto;
import com.iraychev.expenseanalyzer.domain.entity.BankConnection;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", uses = {BankAccountMapper.class})
public interface BankConnectionMapper {
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "accounts", source = "accounts")
    BankConnectionDto toDTO(BankConnection bankConnection);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    BankConnection toEntity(BankConnectionDto dto);
}