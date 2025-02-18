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
    public UserDto getByEmail(@PathVariable String email) {
        log.info("Received request to get User with emai: {}", email);

        return userService.getByEmail(email);
    }

    @ResponseStatus(CREATED)
    @PostMapping
    public UserDto create(@RequestBody UserDto userDto) {
        log.info("Received request to create User: {}", userDto);
        return userService.createUser(userDto);
    }

    @ResponseStatus(OK)
    @PostMapping("/{userEmail}/link-bank")
    public UserDto linkBankConnection(@PathVariable String userEmail,
                                      @RequestParam String requisitionId) {

        if (userEmail == null || requisitionId == null) {
            throw new IllegalArgumentException("User email and requisition ID must be provided");
        }
        
        log.info("Linking user with email: {} with Bank Connection with requisition id: {}", userEmail, requisitionId);
        return userService.linkBankConnection(userEmail, requisitionId);
    }
}
