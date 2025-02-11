package com.iraychev.expenseanalyzer.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler({ResourceNotFoundException.class})
    public ResponseEntity<ExceptionBody> handleNotFoundException(CustomResponseStatusException e) {
        return handler(e);
    }

    @ExceptionHandler(BankIntegrationException.class)
    public ResponseEntity<ErrorResponse> handleIntegrationException(CustomResponseStatusException e) {
        return handler(e);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ExceptionBody> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {

        List<String> errorMessages = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .toList();

        return new ResponseEntity<>(new ExceptionBody(
                ExceptionCode.NULL_REQUEST_FIELDS.getValue(),
                errorMessages.toString(),
                ExceptionCode.NULL_REQUEST_FIELDS.getReason()
        ), e.getStatusCode());
    }

    private ResponseEntity<ExceptionBody> handler(CustomResponseStatusException ex) {

        return new ResponseEntity<>(new ExceptionBody(
                ex.getErrorCode(),
                ex.getReason(),
                ex.getMessage()), ex.getStatusCode()
        );
    }
}