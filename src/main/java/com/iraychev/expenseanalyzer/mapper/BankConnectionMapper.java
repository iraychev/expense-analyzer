package com.iraychev.expenseanalyzer.mapper;

import com.iraychev.expenseanalyzer.domain.entity.BankConnection;
import com.iraychev.expenseanalyzer.dto.BankConnectionDto;
import com.iraychev.expenseanalyzer.domain.entity.BankAccount;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.Comparator;
import java.util.Objects;

@Mapper(componentModel = "spring", uses = {BankAccountMapper.class})
public interface BankConnectionMapper {
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "accounts", source = "accounts")
    BankConnectionDto toDTO(BankConnection bankConnection);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    BankConnection toEntity(BankConnectionDto dto);

    @AfterMapping
    default void fillSyncedAt(BankConnection source, @MappingTarget BankConnectionDto target) {
        if (source.getAccounts() == null) {
            return;
        }
        source.getAccounts().stream()
                .map(BankAccount::getLastSyncedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .ifPresent(target::setSyncedAt);
    }
}