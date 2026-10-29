package com.iraychev.expenseanalyzer.service;

import com.iraychev.expenseanalyzer.domain.entity.BankAccount;
import com.iraychev.expenseanalyzer.domain.entity.BankConnection;
import com.iraychev.expenseanalyzer.domain.entity.User;
import com.iraychev.expenseanalyzer.dto.BankConnectionDto;
import com.iraychev.expenseanalyzer.dto.UserDto;
import com.iraychev.expenseanalyzer.exception.ResourceAlreadyExistsException;
import com.iraychev.expenseanalyzer.exception.ResourceNotFoundException;
import com.iraychev.expenseanalyzer.exception.ValidationException;
import com.iraychev.expenseanalyzer.mapper.UserMapper;
import com.iraychev.expenseanalyzer.repository.BankConnectionRepository;
import com.iraychev.expenseanalyzer.repository.UserRepository;
import com.iraychev.expenseanalyzer.utils.UserValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final BankConnectionService bankConnectionService;
    private final BankConnectionRepository bankConnectionRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    public List<UserDto> getAllUsers() {
        List<User> users = userRepository.findAll();
        users.forEach(user -> user.getBankConnections()
                .forEach(bankConnection -> bankConnection.getAccounts()
                        .forEach(bankAccount -> bankAccount.setTransactions(null))));
        return users.stream().map(userMapper::toDTO).toList();
    }

    public UserDto createUser(UserDto userDto) {
        validateNewUser(userDto);
        // Login lower-cases the username, so it must be stored that way to be able to sign in.
        userDto.setUsername(userDto.getUsername().trim().toLowerCase(Locale.ROOT));
        userDto.setEmail(userDto.getEmail().trim().toLowerCase(Locale.ROOT));
        userDto.setName(userDto.getName().trim());

        if (userRepository.findByUsername(userDto.getUsername()).isPresent()) {
            throw new ResourceAlreadyExistsException("This username is already taken", "username");
        }
        if (userRepository.findByEmail(userDto.getEmail()).isPresent()) {
            throw new ResourceAlreadyExistsException("An account with this email already exists", "email");
        }
        userDto.setPassword(bCryptPasswordEncoder.encode(userDto.getPassword()));

        User savedUser = userRepository.save(userMapper.toEntity(userDto));
        return userMapper.toDTO(savedUser);
    }

    private void validateNewUser(UserDto dto) {
        if (isBlank(dto.getName())) {
            throw new ValidationException("name", "Full name is required");
        }
        if (dto.getName().trim().length() > 100) {
            throw new ValidationException("name", "Full name must be at most 100 characters");
        }
        if (isBlank(dto.getUsername()) || !UserValidator.isValidUsername(dto.getUsername().trim())) {
            throw new ValidationException("username",
                    "Username must be 4-20 characters: letters, digits, '.' or '_' (not at the start or end)");
        }
        if (isBlank(dto.getEmail()) || !UserValidator.isValidEmail(dto.getEmail().trim())) {
            throw new ValidationException("email", "Please enter a valid email address");
        }
        if (dto.getPassword() == null || !UserValidator.isValidPassword(dto.getPassword())) {
            throw new ValidationException("password", "Password must be at least 8 characters and contain a letter and a digit");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public UserDto updateProfile(String username, UserDto userDto) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (userDto.getPassword() != null) {
            userDto.setPassword(bCryptPasswordEncoder.encode(userDto.getPassword()));
        }

        userMapper.updateUserFromDto(userDto, user);
        userRepository.save(user);
        return userMapper.toDTO(user);
    }

    public UserDto updateBankConnection(String username) {
        log.info("Updating bank connections for user with username: {}", username);
        User foundUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        List<String> requisitionIds = foundUser.getBankConnections().stream()
                .map(BankConnection::getRequisitionId)
                .toList();

        List<BankConnection> updatedBankConnections = requisitionIds.stream()
                .map(requisitionId -> bankConnectionService.updateBankConnection(username, requisitionId, false))
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

        log.info("Successfully updated bank connections for user with username: {}", username);
        return userMapper.toDTO(foundUser);
    }

    public UserDto linkBankConnection(String username, String requisitionId) {
        BankConnectionDto updatedBankConnection = bankConnectionService.updateBankConnection(username, requisitionId, true);
        User foundUser = userRepository.findByUsername(username)
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

    public void removeBankConnection(String username, Long bankConnectionId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        BankConnection bankConnection = bankConnectionRepository.findById(bankConnectionId)
                .orElseThrow(() -> new ResourceNotFoundException("BankConnection not found"));

        user.getBankConnections().remove(bankConnection);
        userRepository.save(user);
    }

    public UserDto getUserByUsername(String username) {
        User foundUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        UserDto userDto = userMapper.toDTO(foundUser);
        userDto.getBankConnections().forEach(bankConnectionDto -> bankConnectionDto.getAccounts()
                .forEach(bankAccountDto -> bankAccountDto.setTransactions(null)));

        return userDto;
    }

    public UserDto getUserByUsernameWithTransactions(String username) {
        User foundUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        return userMapper.toDTO(foundUser);
    }

    public void removeUser(String username) {
        User foundUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        userRepository.delete(foundUser);
    }
}