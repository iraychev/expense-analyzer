package com.iraychev.expenseanalyzer.mapper;

import com.iraychev.expenseanalyzer.domain.entity.User;
import com.iraychev.expenseanalyzer.dto.UserDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {BankConnectionMapper.class})
public interface UserMapper {
    @Mapping(target = "bankConnections", source = "bankConnections")
    UserDto toDTO(User user);

    @Mapping(target = "id", ignore = true)
    User toEntity(UserDto userDTO);
}