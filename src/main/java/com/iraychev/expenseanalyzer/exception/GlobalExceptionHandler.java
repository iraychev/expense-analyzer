package com.iraychev.expenseanalyzer.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler({
            ResourceNotFoundException.class,
            BankIntegrationException.class,
            ResourceAlreadyExistsException.class,
            ValidationException.class,
            InvalidCredentialsException.class,
            RateLimitException.class
    })
    public ResponseEntity<ExceptionBody> handleCustomException(CustomResponseStatusException e) {
        return handler(e);
    }

    /** Safety net for races between the existence checks and the unique constraints. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ExceptionBody> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("Data integrity violation", e);
        return new ResponseEntity<>(new ExceptionBody(
                ExceptionCode.ALREADY_EXISTS.getValue(),
                ExceptionCode.ALREADY_EXISTS.getReason(),
                "A record with these details already exists",
                null), HttpStatus.CONFLICT);
    }

    private ResponseEntity<ExceptionBody> handler(CustomResponseStatusException ex) {
        return new ResponseEntity<>(new ExceptionBody(
                ex.getErrorCode(),
                ex.getReason(),
                ex.getMessage(),
                ex.getField()), ex.getStatusCode()
        );
    }
}
