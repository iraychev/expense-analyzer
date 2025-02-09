package com.iraychev.expenseanalyzer.controller;

import com.iraychev.expenseanalyzer.dto.TransactionDTO;
import com.iraychev.expenseanalyzer.service.GoCardlessService;
import com.iraychev.expenseanalyzer.service.TransactionService;
import com.iraychev.expenseanalyzer.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExpenseController.class)
class ExpenseControllerTests {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private TransactionService transactionService;

    @MockitoBean
    private GoCardlessService goCardlessService;

    @Test
    void testGetUserTransactions() throws Exception {
        List<TransactionDTO> mockTransactions = Arrays.asList(
                TransactionDTO.builder()
                        .id(1L)
                        .accountId("ACC123")
                        .amount(new BigDecimal("10.00"))
                        .currency("GBP")
                        .creditorName("Starbucks")
                        .category("Coffee")
                        .bookingDate(LocalDate.now())
                        .build()
        );

        when(transactionService.getTransactionsByUserId(1L))
                .thenReturn(mockTransactions);

        mockMvc.perform(get("/api/users/1/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].creditorName").value("Starbucks"))
                .andExpect(jsonPath("$[0].category").value("Coffee"));
    }
}