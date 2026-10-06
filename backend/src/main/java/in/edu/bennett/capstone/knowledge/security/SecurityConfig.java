package in.edu.bennett.capstone.knowledge.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Default deny (AUTH-8): only the endpoints on the public list answer without authentication. No
 * way to authenticate exists until Phase 1, so every other request gets 401.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

  @Bean
  SecurityFilterChain apiSecurity(
      HttpSecurity http, @Value("${springdoc.api-docs.enabled:false}") boolean apiDocsEnabled)
      throws Exception {
    http.authorizeHttpRequests(
            requests -> {
              requests.requestMatchers(HttpMethod.GET, "/api/v1/meta").permitAll();
              requests.requestMatchers("/actuator/health", "/actuator/health/**").permitAll();
              if (apiDocsEnabled) {
                // API documentation is public in development only (AUTH-8, ADR-014).
                requests
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll();
              }
              requests.anyRequest().authenticated();
            })
        // A stateless API with bearer tokens needs no CSRF protection (AUTH-9).
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        // Unauthenticated means 401, not the 403 Spring Security sends when no login exists.
        .exceptionHandling(
            exceptions ->
                exceptions.authenticationEntryPoint(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
    return http.build();
  }
}
