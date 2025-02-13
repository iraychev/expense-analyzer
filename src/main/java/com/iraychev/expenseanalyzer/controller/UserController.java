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

    @GetMapping
    public ResponseEntity<List<UserDto>> getAllUsers() {
        return new ResponseEntity<>(userService.getAllUsers(), HttpStatus.OK);
    }

    @PostMapping
    public ResponseEntity<User> createUser(@RequestBody UserDto userDto) {
        User createdUser = userService.createUser(userDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
    }

    @PostMapping("/{userEmail}/bank-accounts/link")
    public ResponseEntity<User> linkBankConnection(@PathVariable String userEmail,
                                                @RequestParam String requisitionId) {
        User updatedUser = userService.linkBankConnection(userEmail, requisitionId);
        return ResponseEntity.ok(updatedUser);
    }
}
