package com.iraychev.expenseanalyzer.mapper;

import com.iraychev.expenseanalyzer.dto.UserDTO;
import com.iraychev.expenseanalyzer.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = { TransactionMapper.class, BankAccountMapper.class })
public interface UserMapper {
    UserDTO toDTO(User user);
    User toEntity(UserDTO userDTO);
}