package com.iraychev.expenseanalyzer.exception;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Accessors(chain = true)
public record ExceptionBody(String errorCode, String reason, String message)   {
}
