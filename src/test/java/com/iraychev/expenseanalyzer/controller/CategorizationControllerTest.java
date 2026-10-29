package com.iraychev.expenseanalyzer.controller;

import com.iraychev.expenseanalyzer.categorization.CategorizationWorker;
import com.iraychev.expenseanalyzer.dto.OtherRetryRunResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategorizationControllerTest {

	@Mock
	private CategorizationWorker worker;

	@InjectMocks
	private CategorizationController categorizationController;

	@Test
	void runNowMapsWorkerStatsToTheLegacyResponseShape() {
		when(worker.runNow()).thenReturn(new CategorizationWorker.RunStats(10, 6, 2, 1, 1));

		ResponseEntity<OtherRetryRunResponse> response = categorizationController.runOtherRetryNow();

		assertEquals(200, response.getStatusCode().value());
		OtherRetryRunResponse body = response.getBody();
		assertEquals(10, body.processedCount());
		assertEquals(6, body.recategorizedCount());
		assertEquals(4, body.stillOtherCount());
		assertEquals(1, body.maxRetriesReachedCount());
		verify(worker).runNow();
	}
}
