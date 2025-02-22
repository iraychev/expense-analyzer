package com.iraychev.expenseanalyzer.controller;

import com.iraychev.expenseanalyzer.dto.UserDto;
import com.iraychev.expenseanalyzer.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

@RestController
@RequestMapping("/api/v1/users")
@Slf4j
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @ResponseStatus(CREATED)
    @PostMapping
    public UserDto createUser(@RequestBody UserDto userDto) {
        log.info("Received request to create User: {}", userDto);
        return userService.createUser(userDto);
    }

    @ResponseStatus(OK)
    @GetMapping
    public List<UserDto> getAllUsers() {
        log.info("Getting all users");
        List<UserDto> users = userService.getAllUsers();

        log.debug("Users: {}", users);
        return users;
    }

    @ResponseStatus(OK)
    @GetMapping("/email/{email}")
    public UserDto getUserByEmail(@PathVariable String email) {
        log.info("Received request to get User with email: {}", email);
        return userService.getUserByEmail(email);
    }

    @ResponseStatus(OK)
    @GetMapping("/email/{email}/with-transactions")
    public UserDto getUserByEmailWithTransactions(@PathVariable String email) {
        log.info("Received request to get User with email: {} with transactions", email);
        return userService.getUserByEmailWithTransactions(email);
    }

    @ResponseStatus(CREATED)
    @PostMapping("/email/{email}/bank-connections/link/{requisitionId}")
    public UserDto linkBankConnection(@PathVariable String email,
                                      @PathVariable String requisitionId) {

        if (email == null || requisitionId == null) {
            throw new IllegalArgumentException("User email and requisition ID must be provided");
        }

        log.info("Linking user with email: {} with Bank Connection with requisition id: {}", email, requisitionId);
        return userService.linkBankConnection(email, requisitionId);
    }

    @ResponseStatus(OK)
    @PutMapping("/email/{email}/bank-connections/update")
    public UserDto updateBankConnection(@PathVariable String email) {
        log.info("Updating bank connections for user with email: {}", email);
        return userService.updateBankConnection(email);
    }

    @ResponseStatus(OK)
    @DeleteMapping("/email/{email}/bank-connections/{bankConnectionId}")
    public void removeBankConnection(@PathVariable String email, @PathVariable Long bankConnectionId) {
        log.info("Removing bank connection with id: {} from user with email: {}", bankConnectionId, email);
        userService.removeBankConnection(email, bankConnectionId);
    }
}