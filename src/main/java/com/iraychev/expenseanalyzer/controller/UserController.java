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
    @GetMapping("/username/{username}")
    public UserDto getUserByUsername(@PathVariable String username) {
        log.info("Received request to get User with username: {}", username);
        return userService.getUserByUsername(username);
    }

    @ResponseStatus(OK)
    @GetMapping("/username/{username}/with-transactions")
    public UserDto getUserByUsernameWithTransactions(@PathVariable String username) {
        log.info("Received request to get User with email: {} with transactions", username);
        return userService.getUserByUsernameWithTransactions(username);
    }

    @ResponseStatus(CREATED)
    @PostMapping("/username/{username}/bank-connections/link/{requisitionId}")
    public UserDto linkBankConnection(@PathVariable String username,
                                      @PathVariable String requisitionId) {

        if (username == null || requisitionId == null) {
            throw new IllegalArgumentException("User email and requisition ID must be provided");
        }

        log.info("Linking user with username: {} with Bank Connection with requisition id: {}", username, requisitionId);
        return userService.linkBankConnection(username, requisitionId);
    }

    @PatchMapping("/username/{username}")
    public UserDto updateProfile(@PathVariable String username, @RequestBody UserDto userDto) {
        log.info("Received request to update User profile: {}", userDto);
        return userService.updateProfile(username, userDto);
    }

    @ResponseStatus(OK)
    @PutMapping("/username/{username}/bank-connections/update")
    public UserDto updateBankConnection(@PathVariable String username) {
        log.info("Updating bank connections for user with username: {}", username);
        return userService.updateBankConnection(username);
    }

    @ResponseStatus(OK)
    @DeleteMapping("/username/{username}/bank-connections/{bankConnectionId}")
    public void removeBankConnection(@PathVariable String username, @PathVariable Long bankConnectionId) {
        log.info("Removing bank connection with id: {} from user with username: {}", bankConnectionId, username);
        userService.removeBankConnection(username, bankConnectionId);
    }
}