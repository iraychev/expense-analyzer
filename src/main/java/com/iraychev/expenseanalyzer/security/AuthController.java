package com.iraychev.expenseanalyzer.security;

import com.iraychev.expenseanalyzer.exception.CustomResponseStatusException;
import com.iraychev.expenseanalyzer.exception.InvalidCredentialsException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@RequiredArgsConstructor
public class AuthController {
   private final AuthService authService;

   @PostMapping("/api/v1/token")
   public String token(HttpServletRequest request) {
       try {
           String authHeader = request.getHeader("Authorization");
           return authService.authenticateAndGenerateToken(authHeader);
       } catch (CustomResponseStatusException e) {
           throw e;
       } catch (AuthenticationException e) {
           // Unknown user and wrong password are deliberately indistinguishable.
           log.info("Failed login attempt: {}", e.getClass().getSimpleName());
           throw new InvalidCredentialsException();
       } catch (Exception e) {
           log.warn("Malformed authentication request", e);
           throw new InvalidCredentialsException();
       }
   }
}
