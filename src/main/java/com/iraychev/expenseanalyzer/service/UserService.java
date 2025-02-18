package com.iraychev.expenseanalyzer.service;

import com.iraychev.expenseanalyzer.domain.entity.BankConnection;
import com.iraychev.expenseanalyzer.dto.BankAccountDto;
import com.iraychev.expenseanalyzer.dto.BankConnectionDto;
import com.iraychev.expenseanalyzer.dto.UserDto;
import com.iraychev.expenseanalyzer.domain.entity.User;
import com.iraychev.expenseanalyzer.exception.AlreadyExistingResourceException;
import com.iraychev.expenseanalyzer.exception.ResourceNotFoundException;
import com.iraychev.expenseanalyzer.mapper.UserMapper;
import com.iraychev.expenseanalyzer.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final BankConnectionService bankConnectionService;

    public List<UserDto> getAllUsers() {
        List<User> users = userRepository.findAll();
        return users.stream().map(userMapper::toDTO).toList();
    }

    public UserDto createUser(UserDto userDto) {
        if (userRepository.findByEmail(userDto.getEmail()).isPresent()) {
            // TODO: define new exception
            throw new AlreadyExistingResourceException("User with this email already exists");
        }
        User savedUser = userRepository.save(userMapper.toEntity(userDto));
        return userMapper.toDTO(savedUser);
    }

    public UserDto linkBankConnection(String userEmail, String requisitionId) {
        BankConnectionDto updatedBankConnection = bankConnectionService.syncTransactions(userEmail, requisitionId);
        User foundUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
        BankConnection correctConnection = foundUser.getBankConnections().stream().filter(bankConnection -> bankConnection.getReference().equals(updatedBankConnection.getReference())).toList().getFirst();

        return userMapper.toDTO(foundUser);
    }

    public UserDto getByEmail(String userEmail) {
        User foundUser = userRepository.findByEmail(userEmail).orElseThrow(() -> new ResourceNotFoundException("User not found."));
        return userMapper.toDTO(foundUser);
    }
}