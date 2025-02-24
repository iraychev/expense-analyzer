package com.iraychev.expenseanalyzer.security;

import com.iraychev.expenseanalyzer.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {
   private final AuthenticationManager authenticationManager;
   private final TokenService tokenService;

   public String authenticateAndGenerateToken(String authHeader) {
       if (authHeader == null || !authHeader.startsWith(("Basic "))) {
           throw new ResourceNotFoundException("Missing or invalid Authorization header");
       }

       String base64Credentials = authHeader.substring("Basic ".length());
       String credentials = new String(Base64.getDecoder().decode(base64Credentials));
       String[] values = credentials.split(":", 2);
       String username = values[0];
       String password = values[1];
       username = username.toLowerCase(Locale.ROOT);
       Authentication authentication = authenticationManager.authenticate(
               new UsernamePasswordAuthenticationToken(username, password)
       );

       if (!authentication.isAuthenticated()) {
           throw new ResourceNotFoundException("Invalid user request");
       }
       return tokenService.generateToken(authentication);
   }
}
