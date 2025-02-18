package com.iraychev.expenseanalyzer.mapper;

import com.iraychev.expenseanalyzer.domain.entity.BankConnection;
import com.iraychev.expenseanalyzer.dto.BankConnectionDto;
import com.iraychev.expenseanalyzer.dto.UserDto;
import com.iraychev.expenseanalyzer.domain.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", uses = {BankConnectionMapper.class})
public interface UserMapper {
    @Mapping(target = "bankConnections", source = "bankConnections")
    UserDto toDTO(User user);

    @Mapping(target = "id", ignore = true)
    User toEntity(UserDto userDTO);
}
