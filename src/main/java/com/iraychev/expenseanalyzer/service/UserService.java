package com.iraychev.expenseanalyzer.service;

import com.iraychev.expenseanalyzer.domain.entity.BankConnection;
import com.iraychev.expenseanalyzer.dto.BankConnectionDto;
import com.iraychev.expenseanalyzer.dto.UserDto;
import com.iraychev.expenseanalyzer.domain.entity.User;
import com.iraychev.expenseanalyzer.exception.ResourceNotFoundException;
import com.iraychev.expenseanalyzer.mapper.UserMapper;
import com.iraychev.expenseanalyzer.repository.BankConnectionRepository;
import com.iraychev.expenseanalyzer.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final BankConnectionRepository BankConnectionRepository;
    private final GoCardlessIntegrationService goCardlessIntegrationService;
    private final TransactionService transactionService;
    private final UserMapper userMapper;

    public List<UserDto> getAllUsers() {
        List<User> users = userRepository.findAll();
        return users.stream().map(userMapper::toDTO).toList();
    }

    public User createUser(UserDto userDto) {
        if (userRepository.findByEmail(userDto.getEmail()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User with this email already exists");
        }
        User user = User.builder()
                .email(userDto.getEmail())
                .name(userDto.getName())
                .build();
        return userRepository.save(user);
    }

    public User linkBankConnection(Long userId, BankConnectionDto bankConnectionDto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        boolean exists = user.getBankConnections().stream()
                .anyMatch(account -> account.getAccountId().equals(bankConnectionDto.getExternalAccountId()));
        if (exists) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bank account already connected");
        }
        BankConnection bankConnection = BankConnection.builder()
                .accountId(bankConnectionDto.getExternalAccountId())
                .accountName(bankConnectionDto.getAccountName())
                .institutionId(bankConnectionDto.getInstitutionId())
                .institutionName(bankConnectionDto.getInstitutionName())
                .user(user)
                .build();
        user.getBankConnections().add(bankConnection);
        return userRepository.save(user);
    }
}