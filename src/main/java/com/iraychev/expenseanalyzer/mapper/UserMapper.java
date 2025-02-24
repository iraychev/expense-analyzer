package com.iraychev.expenseanalyzer.mapper;

import com.iraychev.expenseanalyzer.domain.entity.User;
import com.iraychev.expenseanalyzer.dto.UserDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring",
        uses = {BankConnectionMapper.class},
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface UserMapper {
    @Mapping(target = "bankConnections", source = "bankConnections")
    UserDto toDTO(User user);

    @Mapping(target = "id", ignore = true)
    User toEntity(UserDto userDTO);

    void updateUserFromDto(UserDto userDto, @MappingTarget User user);

}