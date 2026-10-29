package com.iraychev.expenseanalyzer.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CategoryServiceUnknownVendorTest {

    @Test
    void neverReadsOrWritesCacheForThePlaceholderVendor() {
        VendorCategoryMappingService mappings = mock(VendorCategoryMappingService.class);
        AiCategorizationService ai = mock(AiCategorizationService.class);
        when(ai.categorizeRemittance("Salary from ACME")).thenReturn("Shopping");
        CategoryService service = new CategoryService(mappings, ai);

        assertEquals("Shopping", service.categorizeTransaction("Salary from ACME"));

        verify(mappings, never()).getMappingForVendor(any());
        verify(mappings, never()).save(any());
    }
}
