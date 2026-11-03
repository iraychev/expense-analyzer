package com.iraychev.expenseanalyzer.service;

import com.iraychev.expenseanalyzer.domain.entity.VendorCategoryMapping;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
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
        when(aiCategorizationService.categorizeRemittances(List.of(remittanceInfo))).thenReturn(List.of("Food"));

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
        when(aiCategorizationService.categorizeRemittances(List.of(remittanceInfo))).thenReturn(List.of("Food"));

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
        verify(aiCategorizationService, never()).categorizeRemittances(any());
        verify(vendorCategoryMappingService, never()).save(existing);
    }

    @Test
    void descriptionsWithoutVendorAreNotCachedAndDoNotShareACategory() {
        when(aiCategorizationService.categorizeRemittances(List.of("Salary", "Insurance premium")))
                .thenReturn(List.of("Financial Services", "Health and Beauty"));

        List<String> result = categoryService.categorizeTransactions(List.of("Salary", "Insurance premium"));

        assertEquals(List.of("Financial Services", "Health and Beauty"), result);
        verify(vendorCategoryMappingService, never()).save(any());
    }

    @Test
    void sameVendorIsSentToTheAiOnce() {
        String first = "ПОС 1.00 BGN авт.код:1-HK BAR GYUESHEVO/Sofia/TID:1";
        String second = "ПОС 2.00 BGN авт.код:2-HK BAR GYUESHEVO/Sofia/TID:1";
        when(vendorCategoryMappingService.getMappingForVendor("hk bar gyueshevo")).thenReturn(Optional.empty());
        when(aiCategorizationService.categorizeRemittances(List.of(first))).thenReturn(List.of("Restaurants and bars"));

        List<String> result = categoryService.categorizeTransactions(List.of(first, second));

        assertEquals(List.of("Restaurants and bars", "Restaurants and bars"), result);
        verify(vendorCategoryMappingService).save(any());
    }
}

