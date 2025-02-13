package com.iraychev.expenseanalyzer.controller;

import com.iraychev.expenseanalyzer.dto.BankConnectionDto;
import com.iraychev.expenseanalyzer.dto.UserDto;
import com.iraychev.expenseanalyzer.domain.entity.User;
import com.iraychev.expenseanalyzer.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@Slf4j
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @ResponseStatus(OK)
    @GetMapping
    public List<UserDto> getAll() {
        log.info("Getting all users");
        List<UserDto> users = userService.getAllUsers();

        log.debug("Users: {}", users);
        return users;
    }

    @ResponseStatus(OK)
    @GetMapping("/email/{email}")
    public UserDto getUserByEmail(String email) {
        log.info("Received request to get User with emai: {}", email);

        return userService.getUserByEmail(email);
    }

    @ResponseStatus(CREATED)
    @PostMapping
    public UserDto create(@RequestBody UserDto userDto) {
        log.info("Received request to create User: {}", userRequest);
        return userService.createUser(userDto);
    }

    @ResponseStatus(OK)
    @PostMapping("/{userEmail}/link-bank")
    public UserDto linkBankConnection(@PathVariable String userEmail,
                                                @RequestParam String requisitionId) {
        
        log.info("Linking user with email: {} with Bank Connection with requisition id: {}", userEmail, requisitionId);
        return userService.linkBankConnection(userEmail, requisitionId);
    }
}
