package com.iraychev.expenseanalyzer.service;

import com.iraychev.expenseanalyzer.domain.entity.VendorCategoryMapping;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private VendorCategoryMappingService vendorCategoryMappingService;

    @Mock
    private AiCategorizationService aiCategorizationService;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void categorizeTransactionUpdatesExistingOtherMapping() {
        String remittanceInfo = "авт.код:123-STARBUCKS";
        VendorCategoryMapping existing = VendorCategoryMapping.builder()
                .id(42L)
                .vendor("starbucks")
                .category("Other")
                .build();

        when(vendorCategoryMappingService.getMappingForVendor("starbucks")).thenReturn(Optional.of(existing));
        when(aiCategorizationService.categorizeRemittance(remittanceInfo)).thenReturn("Food");

        String result = categoryService.categorizeTransaction(remittanceInfo);

        assertEquals("Food", result);
        ArgumentCaptor<VendorCategoryMapping> captor = ArgumentCaptor.forClass(VendorCategoryMapping.class);
        verify(vendorCategoryMappingService).save(captor.capture());
        assertEquals(42L, captor.getValue().getId());
        assertEquals("starbucks", captor.getValue().getVendor());
        assertEquals("Food", captor.getValue().getCategory());
    }

    @Test
    void categorizeTransactionCreatesMappingWhenVendorDoesNotExist() {
        String remittanceInfo = "авт.код:123-STARBUCKS";

        when(vendorCategoryMappingService.getMappingForVendor("starbucks")).thenReturn(Optional.empty());
        when(aiCategorizationService.categorizeRemittance(remittanceInfo)).thenReturn("Food");

        String result = categoryService.categorizeTransaction(remittanceInfo);

        assertEquals("Food", result);
        ArgumentCaptor<VendorCategoryMapping> captor = ArgumentCaptor.forClass(VendorCategoryMapping.class);
        verify(vendorCategoryMappingService).save(captor.capture());
        assertNull(captor.getValue().getId());
        assertEquals("starbucks", captor.getValue().getVendor());
        assertEquals("Food", captor.getValue().getCategory());
    }

    @Test
    void categorizeTransactionReturnsExistingNonOtherCategoryWithoutAiCall() {
        String remittanceInfo = "авт.код:123-STARBUCKS";
        VendorCategoryMapping existing = VendorCategoryMapping.builder()
                .id(42L)
                .vendor("starbucks")
                .category("Groceries")
                .build();

        when(vendorCategoryMappingService.getMappingForVendor("starbucks")).thenReturn(Optional.of(existing));

        String result = categoryService.categorizeTransaction(remittanceInfo);

        assertEquals("Groceries", result);
        verify(aiCategorizationService, never()).categorizeRemittance(remittanceInfo);
        verify(vendorCategoryMappingService, never()).save(existing);
    }
}

