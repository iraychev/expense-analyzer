package com.iraychev.expenseanalyzer.controller;

import com.iraychev.expenseanalyzer.dto.UserDTO;
import com.iraychev.expenseanalyzer.entity.User;
import com.iraychev.expenseanalyzer.mapper.UserMapper;
import com.iraychev.expenseanalyzer.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;

    // Create a new user
    @PostMapping
    public ResponseEntity<UserDTO> createUser(@RequestBody UserDTO userDTO) {
        // Convert DTO to entity, create the user, then convert back to DTO.
        User createdUser = userService.createUser(userMapper.toEntity(userDTO));
        return ResponseEntity.status(HttpStatus.CREATED).body(userMapper.toDTO(createdUser));
    }

    // Get user details (including connected bank accounts and transactions if embedded in the DTO)
    @GetMapping("/{userId}")
    public ResponseEntity<UserDTO> getUser(@PathVariable Long userId) {
        UserDTO userDTO = userService.getUserById(userId);
        return ResponseEntity.ok(userDTO);
    }
}
