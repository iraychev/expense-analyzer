package com.iraychev.expenseanalyzer.security;

import com.iraychev.expenseanalyzer.exception.GlobalExceptionHandler;
import com.iraychev.expenseanalyzer.exception.InvalidCredentialsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private AuthService authService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void wrongPasswordAndUnknownUserGiveTheSameNeutralError() throws Exception {
        when(authService.authenticateAndGenerateToken("Basic x")).thenThrow(new BadCredentialsException("bad"));
        when(authService.authenticateAndGenerateToken("Basic y"))
                .thenThrow(new InternalAuthenticationServiceException("User not found"));

        for (String header : new String[]{"Basic x", "Basic y"}) {
            mockMvc.perform(post("/api/v1/token").header("Authorization", header))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("ERR005"))
                    .andExpect(jsonPath("$.message").value("Incorrect username or password"));
        }
    }

    @Test
    void missingHeaderIsAlsoNeutral() throws Exception {
        when(authService.authenticateAndGenerateToken(null)).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/v1/token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Incorrect username or password"));
    }
}
