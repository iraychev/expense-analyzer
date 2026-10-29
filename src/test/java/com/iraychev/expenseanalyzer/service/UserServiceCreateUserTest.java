package com.iraychev.expenseanalyzer.service;

import com.iraychev.expenseanalyzer.domain.entity.User;
import com.iraychev.expenseanalyzer.dto.UserDto;
import com.iraychev.expenseanalyzer.exception.ResourceAlreadyExistsException;
import com.iraychev.expenseanalyzer.exception.ValidationException;
import com.iraychev.expenseanalyzer.mapper.UserMapper;
import com.iraychev.expenseanalyzer.repository.BankConnectionRepository;
import com.iraychev.expenseanalyzer.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceCreateUserTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserMapper userMapper;
    @Mock
    private BankConnectionService bankConnectionService;
    @Mock
    private BankConnectionRepository bankConnectionRepository;
    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private UserDto validDto() {
        return UserDto.builder()
                .name("Ivan Petrov")
                .username("Ivan.Petrov")
                .email("Ivan@Example.com")
                .password("secret123")
                .build();
    }

    @Test
    void rejectsInvalidEmailWithFieldName() {
        UserDto dto = validDto();
        dto.setEmail("not-an-email");

        ValidationException e = assertThrows(ValidationException.class, () -> userService.createUser(dto));

        assertEquals("email", e.getField());
        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectsWeakPassword() {
        UserDto dto = validDto();
        dto.setPassword("short1");

        ValidationException e = assertThrows(ValidationException.class, () -> userService.createUser(dto));

        assertEquals("password", e.getField());
    }

    @Test
    void rejectsPasswordWithoutDigit() {
        UserDto dto = validDto();
        dto.setPassword("onlyletters");

        assertEquals("password", assertThrows(ValidationException.class, () -> userService.createUser(dto)).getField());
    }

    @Test
    void acceptsNonLatinFullName() {
        UserDto dto = validDto();
        dto.setName("Иван Петров");
        when(userRepository.findByUsername("ivan.petrov")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("ivan@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret123")).thenReturn("hash");
        when(userMapper.toEntity(any(UserDto.class))).thenReturn(new User());

        userService.createUser(dto);

        verify(userRepository).save(any(User.class));
    }

    @Test
    void reportsTakenUsernameOnUsernameField() {
        when(userRepository.findByUsername("ivan.petrov")).thenReturn(Optional.of(new User()));

        ResourceAlreadyExistsException e =
                assertThrows(ResourceAlreadyExistsException.class, () -> userService.createUser(validDto()));

        assertEquals("username", e.getField());
    }

    @Test
    void reportsTakenEmailOnEmailField() {
        when(userRepository.findByUsername("ivan.petrov")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("ivan@example.com")).thenReturn(Optional.of(new User()));

        ResourceAlreadyExistsException e =
                assertThrows(ResourceAlreadyExistsException.class, () -> userService.createUser(validDto()));

        assertEquals("email", e.getField());
        verify(userRepository, never()).save(any());
    }

    @Test
    void storesUsernameAndEmailLowerCased() {
        when(userRepository.findByUsername("ivan.petrov")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("ivan@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret123")).thenReturn("hash");
        when(userMapper.toEntity(any(UserDto.class))).thenReturn(new User());

        userService.createUser(validDto());

        ArgumentCaptor<UserDto> captor = ArgumentCaptor.forClass(UserDto.class);
        verify(userMapper).toEntity(captor.capture());
        assertEquals("ivan.petrov", captor.getValue().getUsername());
        assertEquals("ivan@example.com", captor.getValue().getEmail());
    }
}
