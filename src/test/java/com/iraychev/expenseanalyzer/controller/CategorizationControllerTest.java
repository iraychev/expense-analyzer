package com.iraychev.expenseanalyzer.controller;

import com.iraychev.expenseanalyzer.dto.OtherRetryRunResponse;
import com.iraychev.expenseanalyzer.service.OtherCategoryRetryScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategorizationControllerTest {

	@Mock
	private OtherCategoryRetryScheduler otherCategoryRetryScheduler;

	@InjectMocks
	private CategorizationController categorizationController;

	@Test
	void runOtherRetryNowReturnsRetryStats() {
		OtherRetryRunResponse expected = new OtherRetryRunResponse(
				LocalDateTime.of(2026, 4, 15, 10, 30),
				10,
				6,
				4,
				1
		);
		when(otherCategoryRetryScheduler.retryOtherCategoriesNow()).thenReturn(expected);

		ResponseEntity<OtherRetryRunResponse> response = categorizationController.runOtherRetryNow();

		assertEquals(200, response.getStatusCode().value());
		assertEquals(expected, response.getBody());
		verify(otherCategoryRetryScheduler).retryOtherCategoriesNow();
	}
}

