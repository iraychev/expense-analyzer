package com.iraychev.expenseanalyzer.service;

import com.iraychev.expenseanalyzer.dto.RequisitionDto;
import com.iraychev.expenseanalyzer.dto.RequisitionRequestDto;
import com.iraychev.expenseanalyzer.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BankConnectionReferenceTest {

    @Mock
    private GoCardlessIntegrationService goCardlessIntegrationService;

    @InjectMocks
    private BankConnectionService bankConnectionService;

    @Test
    void sendsUniqueReferenceToGoCardlessAndKeepsNameRecoverable() {
        when(goCardlessIntegrationService.createRequisition(any())).thenReturn(new RequisitionDto());

        bankConnectionService.createRequisition(RequisitionRequestDto.builder().reference("Ucb").build());
        bankConnectionService.createRequisition(RequisitionRequestDto.builder().reference("Ucb").build());

        ArgumentCaptor<RequisitionRequestDto> captor = ArgumentCaptor.forClass(RequisitionRequestDto.class);
        verify(goCardlessIntegrationService, org.mockito.Mockito.times(2)).createRequisition(captor.capture());
        String first = captor.getAllValues().get(0).getReference();
        String second = captor.getAllValues().get(1).getReference();

        assertNotEquals(first, second);
        assertTrue(first.startsWith("Ucb_"));
        assertEquals("Ucb", BankConnectionService.toDisplayReference(first));
    }

    @Test
    void rejectsBlankReference() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> bankConnectionService.createRequisition(RequisitionRequestDto.builder().reference("  ").build()));

        assertEquals("reference", e.getField());
        verify(goCardlessIntegrationService, never()).createRequisition(any());
    }

    @Test
    void displayReferenceOnlyStripsOurSuffix() {
        assertEquals("My_Main_Account", BankConnectionService.toDisplayReference("My_Main_Account_a1b2c3d4"));
        assertEquals("legacy_name", BankConnectionService.toDisplayReference("legacy_name"));
        assertEquals("Ucb", BankConnectionService.toDisplayReference("Ucb"));
    }
}
