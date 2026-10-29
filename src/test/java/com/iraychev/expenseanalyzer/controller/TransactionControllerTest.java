package com.iraychev.expenseanalyzer.controller;

import com.iraychev.expenseanalyzer.domain.enums.CategorySource;
import com.iraychev.expenseanalyzer.domain.enums.CategoryStatus;
import com.iraychev.expenseanalyzer.dto.CategoryUpdateRequest;
import com.iraychev.expenseanalyzer.dto.TransactionDto;
import com.iraychev.expenseanalyzer.exception.GlobalExceptionHandler;
import com.iraychev.expenseanalyzer.exception.ResourceNotFoundException;
import com.iraychev.expenseanalyzer.service.TransactionCategoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransactionControllerTest {

    private final TransactionCategoryService service = mock(TransactionCategoryService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new TransactionController(service))
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("alice", "x"));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void usesTheAuthenticatedUserAndReturnsTheUpdatedTransaction() throws Exception {
        TransactionDto updated = TransactionDto.builder().id(7L).category("Fees").merchant("oldes")
                .categoryStatus(CategoryStatus.CATEGORIZED).categorySource(CategorySource.USER).build();
        when(service.updateCategory(eq("alice"), eq(7L), org.mockito.ArgumentMatchers.any())).thenReturn(updated);

        mvc.perform(put("/api/v1/transactions/7/category").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"Fees\",\"applyToMerchant\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.category").value("Fees"))
                .andExpect(jsonPath("$.categoryStatus").value("CATEGORIZED"))
                .andExpect(jsonPath("$.categorySource").value("USER"))
                .andExpect(jsonPath("$.merchant").value("oldes"));

        ArgumentCaptor<CategoryUpdateRequest> captor = ArgumentCaptor.forClass(CategoryUpdateRequest.class);
        verify(service).updateCategory(eq("alice"), eq(7L), captor.capture());
        assertEquals("Fees", captor.getValue().getCategory());
        assertTrue(captor.getValue().isApplyToMerchant());
    }

    @Test
    void notYoursIsA404WithTheStandardErrorBody() throws Exception {
        when(service.updateCategory(eq("alice"), eq(8L), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new ResourceNotFoundException("Transaction not found"));

        mvc.perform(put("/api/v1/transactions/8/category").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"Fees\",\"applyToMerchant\":false}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ERR001"))
                .andExpect(jsonPath("$.message").value("Transaction not found"));
    }
}
