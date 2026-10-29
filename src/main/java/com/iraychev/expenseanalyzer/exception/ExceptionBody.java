package com.iraychev.expenseanalyzer.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Error payload. {@code message} is safe to show to end users; {@code field} names the request
 * field the error refers to (if any) so clients can highlight it.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExceptionBody(String errorCode, String reason, String message, String field) {
}
