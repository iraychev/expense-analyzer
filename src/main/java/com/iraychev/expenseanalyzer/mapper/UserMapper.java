package com.iraychev.expenseanalyzer.mapper;

import com.iraychev.expenseanalyzer.dto.UserDto;
import com.iraychev.expenseanalyzer.domain.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = { TransactionMapper.class, BankConnectionMapper.class })
public interface UserMapper {
    UserDto toDTO(User user);
    User toEntity(UserDto userDTO);
}