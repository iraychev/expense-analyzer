package com.iraychev.expenseanalyzer.security;

import com.iraychev.expenseanalyzer.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
           log.debug("Authorization Header: {}", authHeader);

           return authService.authenticateAndGenerateToken(authHeader);
       } catch (Exception e) {
           log.error("Authentication failed", e);
           throw new ResourceNotFoundException("User Not Found");
       }
   }
}
