package com.iraychev.expenseanalyzer.service;

import com.iraychev.expenseanalyzer.dto.BankConnectionDto;
import com.iraychev.expenseanalyzer.dto.UserDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserServiceSyncInfoTest {

    private static BankConnectionDto sync(long id, boolean throttled, Instant next) {
        return BankConnectionDto.builder().id(id).throttled(throttled).nextSyncAvailableAt(next).build();
    }

    @Test
    void allThrottledReportsEarliestNextSync() {
        Instant early = Instant.parse("2026-01-01T10:00:00Z");
        Instant late = Instant.parse("2026-01-01T12:00:00Z");
        UserDto user = UserDto.builder().bankConnections(List.of(
                BankConnectionDto.builder().id(1L).build(), BankConnectionDto.builder().id(2L).build())).build();

        UserService.applySyncInfo(user, Map.of(1L, sync(1, true, late), 2L, sync(2, true, early)));

        assertTrue(user.getThrottled());
        assertEquals(early, user.getNextSyncAvailableAt());
        assertEquals(late, user.getBankConnections().get(0).getNextSyncAvailableAt());
    }

    @Test
    void partiallySyncedIsNotThrottled() {
        UserDto user = UserDto.builder().bankConnections(List.of(
                BankConnectionDto.builder().id(1L).build(), BankConnectionDto.builder().id(2L).build())).build();

        UserService.applySyncInfo(user, Map.of(1L, sync(1, true, Instant.now()), 2L, sync(2, false, null)));

        assertFalse(user.getThrottled());
        assertNull(user.getNextSyncAvailableAt());
    }
}
