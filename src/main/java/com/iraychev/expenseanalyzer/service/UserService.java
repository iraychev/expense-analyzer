package com.iraychev.expenseanalyzer.service;

import com.iraychev.expenseanalyzer.domain.entity.BankAccount;
import com.iraychev.expenseanalyzer.domain.entity.BankConnection;
import com.iraychev.expenseanalyzer.dto.BankConnectionDto;
import com.iraychev.expenseanalyzer.dto.UserDto;
import com.iraychev.expenseanalyzer.domain.entity.User;
import com.iraychev.expenseanalyzer.exception.ResourceAlreadyExistsException;
import com.iraychev.expenseanalyzer.exception.ResourceNotFoundException;
import com.iraychev.expenseanalyzer.mapper.TransactionMapper;
import com.iraychev.expenseanalyzer.mapper.UserMapper;
import com.iraychev.expenseanalyzer.repository.BankConnectionRepository;
import com.iraychev.expenseanalyzer.repository.TransactionRepository;
import com.iraychev.expenseanalyzer.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final BankConnectionService bankConnectionService;
    private final BankConnectionRepository bankConnectionRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;

    public List<UserDto> getAllUsers() {
        List<User> users = userRepository.findAll();
        return users.stream().map(userMapper::toDTO).toList();
    }

    public UserDto createUser(UserDto userDto) {
        if (userRepository.findByEmail(userDto.getEmail()).isPresent()) {
            throw new ResourceAlreadyExistsException("User with this email already exists");
        }
        User savedUser = userRepository.save(userMapper.toEntity(userDto));
        return userMapper.toDTO(savedUser);
    }

    public UserDto updateBankConnection(String userEmail) {
        User foundUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        List<String> requisitionIds = foundUser.getBankConnections().stream()
                .map(BankConnection::getRequisitionId)
                .toList();

        List<BankConnection> updatedBankConnections = requisitionIds.stream()
                .map(requisitionId -> bankConnectionService.updateBankConnection(userEmail, requisitionId, false))
                .map(bankConnectionDto -> bankConnectionRepository.findById(bankConnectionDto.getId())
                        .orElseThrow(() -> new ResourceNotFoundException("BankConnection not found")))
                .toList();

        List<BankConnection> userBankConnections = new ArrayList<>(foundUser.getBankConnections());
        for (BankConnection updatedBankConnection : updatedBankConnections) {
            BankConnection existingConnection = userBankConnections.stream()
                    .filter(conn -> conn.getRequisitionId().equals(updatedBankConnection.getRequisitionId()))
                    .findFirst()
                    .orElse(null);

            if (existingConnection != null) {
                // Merge bank accounts
                for (BankAccount updatedAccount : updatedBankConnection.getAccounts()) {
                    if (!existingConnection.getAccounts().contains(updatedAccount)) {
                        existingConnection.getAccounts().add(updatedAccount);
                    }
                }
            } else {
                userBankConnections.add(updatedBankConnection);
            }
        }

        foundUser.setBankConnections(userBankConnections);
        userRepository.save(foundUser);
        return userMapper.toDTO(foundUser);
    }

    public UserDto linkBankConnection(String userEmail, String requisitionId) {
        BankConnectionDto updatedBankConnection = bankConnectionService.updateBankConnection(userEmail, requisitionId, true);
        User foundUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        BankConnection bankConnection = bankConnectionRepository.findById(updatedBankConnection.getId())
                .orElseThrow(() -> new ResourceNotFoundException("BankConnection not found"));

        List<BankConnection> userBankConnections = foundUser.getBankConnections();
        if (!userBankConnections.contains(bankConnection)) {
            userBankConnections.add(bankConnection);
        }

        foundUser.setBankConnections(userBankConnections);
        userRepository.save(foundUser);

        return userMapper.toDTO(foundUser);
    }

    public void removeBankConnection(String userEmail, Long bankConnectionId) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        BankConnection bankConnection = bankConnectionRepository.findById(bankConnectionId)
                .orElseThrow(() -> new ResourceNotFoundException("BankConnection not found"));

        user.getBankConnections().remove(bankConnection);
        userRepository.save(user);
    }

    public UserDto getUserByEmail(String userEmail) {
        User foundUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        UserDto userDto = userMapper.toDTO(foundUser);
        userDto.getBankConnections().forEach(bankConnectionDto -> bankConnectionDto.getAccounts().forEach(bankAccountDto -> {
            bankAccountDto.setTransactions(null);
        }));

        return userDto;
    }

    public UserDto getUserByEmailWithTransactions(String userEmail) {
        User foundUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        return userMapper.toDTO(foundUser);
    }
}