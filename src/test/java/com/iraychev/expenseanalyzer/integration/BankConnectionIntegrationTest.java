package com.iraychev.expenseanalyzer.integration;

import com.iraychev.expenseanalyzer.dto.UserDTO;
import com.iraychev.expenseanalyzer.entity.User;
import com.iraychev.expenseanalyzer.service.BankConnectionService;
import com.iraychev.expenseanalyzer.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class BankConnectionIntegrationTest {
    @Autowired
    private BankConnectionService bankConnectionService;

    @Autowired
    private UserService userService;

    @Test
    void testBankConnectionFlow() {
        // Create test user
        User testUser = User.builder()
                .username("test.user")
                .email("test@example.com")
                .build();
        UserDTO savedUser = userService.saveUser(testUser);

        // Initiate connection
        String authUrl = bankConnectionService.initiateBankConnection(savedUser.getId());
        assertThat(authUrl).isNotNull();

        // Note: Cannot test callback in unit tests as it requires real GoCardless interaction
        // For manual testing, use the sandbox environment
    }
}