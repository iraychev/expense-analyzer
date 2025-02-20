package com.iraychev.expenseanalyzer.exception;

import lombok.experimental.Accessors;

@Accessors(chain = true)
public record ExceptionBody(String reason, String message) {
}
