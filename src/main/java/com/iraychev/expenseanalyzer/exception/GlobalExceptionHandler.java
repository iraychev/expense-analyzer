package com.iraychev.expenseanalyzer.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler({ResourceNotFoundException.class})
    public ResponseEntity<ExceptionBody> handleNotFoundException(CustomResponseStatusException e) {
        return handler(e);
    }

    @ExceptionHandler(BankIntegrationException.class)
    public ResponseEntity<ExceptionBody> handleIntegrationException(CustomResponseStatusException e) {
        return handler(e);
    }

    @ExceptionHandler(ResourceAlreadyExistsException.class)
    public ResponseEntity<ExceptionBody> handleAlreadyExistsException(CustomResponseStatusException e) {
        return handler(e);
    }
    
    private ResponseEntity<ExceptionBody> handler(CustomResponseStatusException ex) {

        return new ResponseEntity<>(new ExceptionBody(
                ex.getErrorCode(),
                ex.getReason(),
                ex.getMessage()), ex.getStatusCode()
        );
    }
}