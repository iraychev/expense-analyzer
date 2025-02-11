package com.iraychev.expenseanalyzer.service;

import com.iraychev.expenseanalyzer.domain.entity.BankAccount;
import com.iraychev.expenseanalyzer.domain.entity.Transaction;
import com.iraychev.expenseanalyzer.domain.enums.TransactionType;
import com.iraychev.expenseanalyzer.dto.BankAccountDto;
import com.iraychev.expenseanalyzer.dto.UserDto;
import com.iraychev.expenseanalyzer.domain.entity.User;
import com.iraychev.expenseanalyzer.exception.ResourceNotFoundException;
import com.iraychev.expenseanalyzer.mapper.UserMapper;
import com.iraychev.expenseanalyzer.repository.BankAccountRepository;
import com.iraychev.expenseanalyzer.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final BankAccountRepository bankAccountRepository;
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

    public User linkBankAccount(Long userId, BankAccountDto bankAccountDto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        boolean exists = user.getBankAccounts().stream()
                .anyMatch(account -> account.getAccountId().equals(bankAccountDto.getExternalAccountId()));
        if (exists) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bank account already connected");
        }
        BankAccount bankAccount = BankAccount.builder()
                .accountId(bankAccountDto.getExternalAccountId())
                .accountName(bankAccountDto.getAccountName())
                .institutionId(bankAccountDto.getInstitutionId())
                .institutionName(bankAccountDto.getInstitutionName())
                .user(user)
                .build();
        user.getBankAccounts().add(bankAccount);
        return userRepository.save(user);
    }
}