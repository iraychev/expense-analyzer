package com.iraychev.expenseanalyzer.security;

import com.iraychev.expenseanalyzer.config.properties.RsaKeyProperties;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

   private final RsaKeyProperties rsaKeys;

   @Bean
   public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
       return http
               .csrf(AbstractHttpConfigurer::disable)
               .cors(cors -> cors.configurationSource(corsConfigurationSource()))
               .headers(headers -> headers
                       .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin
                       )
               )
               .authorizeHttpRequests(auth -> auth
                       .requestMatchers(HttpMethod.POST, "/api/v1/token").permitAll()
                       .requestMatchers(HttpMethod.POST, "/api/v1/users").permitAll()
                       .requestMatchers(
                                antMatcher("/swagger-ui.html"),
                                antMatcher("/swagger-ui/**"),
                                antMatcher("/v3/api-docs/**")
                        ).permitAll()
                       .anyRequest().authenticated()
               )
               .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
               .oauth2ResourceServer(oauth2 -> oauth2
                       .jwt(Customizer.withDefaults())
               ).exceptionHandling(ex -> ex
                       .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                       .accessDeniedHandler(new BearerTokenAccessDeniedHandler()))
               .build();
   }

   @Bean
   CorsConfigurationSource corsConfigurationSource() {
       CorsConfiguration config = new CorsConfiguration();
       // default react native localhost port
       config.addAllowedOrigin("http://localhost:8081");
       config.addAllowedMethod("*");
       config.addAllowedHeader("*");
       config.setAllowCredentials(true);

       UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
       source.registerCorsConfiguration("/**", config);
       return source;
   }

   /**
    * Configures the JWT decoder.
    *
    * @return the configured JwtDecoder
    */
   @Bean
   JwtDecoder jwtDecoder() {
       return NimbusJwtDecoder.withPublicKey(rsaKeys.publicKey()).build();
   }

   /**
    * Configures the JWT encoder.
    *
    * @return the configured JwtEncoder
    */
   @Bean
   JwtEncoder jwtEncoder() {
       JWK jwk = new RSAKey.Builder(rsaKeys.publicKey()).privateKey(rsaKeys.privateKey()).build();
       JWKSource<SecurityContext> jwks = new ImmutableJWKSet<>(new JWKSet(jwk));
       return new NimbusJwtEncoder(jwks);
   }

   /**
    * Configures the UserDetailsService.
    *
    * @return the configured UserDetailsService
    */
   @Bean
   public UserDetailsService userDetailsService() {
       return new UserDetailsServiceImpl();
   }

   /**
    * Configures the authentication provider.
    *
    * @return the configured AuthenticationProvider
    */
   @Bean
   public AuthenticationProvider authenticationProvider() {
       DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider();
       authenticationProvider.setUserDetailsService(userDetailsService());
       authenticationProvider.setPasswordEncoder(bCryptPasswordEncoder());
       return authenticationProvider;
   }

   /**
    * Configures the authentication manager.
    *
    * @param config the AuthenticationConfiguration object to configure
    * @return the configured AuthenticationManager
    * @throws Exception if an error occurs during configuration
    */
   @Bean
   public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
       return config.getAuthenticationManager();
   }

   /**
    * Configures the BCrypt password encoder.
    *
    * @return the configured BCryptPasswordEncoder
    */
   @Bean
   // This should be called every time a user password is receieved (e.g. creating a user, getting a user by username and password, updating a user)
   public BCryptPasswordEncoder bCryptPasswordEncoder() {
       return new BCryptPasswordEncoder();
   }
}