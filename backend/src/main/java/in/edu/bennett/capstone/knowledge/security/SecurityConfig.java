package in.edu.bennett.capstone.knowledge.security;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Default deny (AUTH-8): only the endpoints on the public list answer without authentication.
 * Failures are Problem Details (ADR-023).
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

  @Bean
  SecurityFilterChain apiSecurity(
      HttpSecurity http,
      @Value("${springdoc.api-docs.enabled:false}") boolean apiDocsEnabled,
      @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver)
      throws Exception {
    ProblemDetailsSecurityHandler problems = new ProblemDetailsSecurityHandler(resolver);
    http.authorizeHttpRequests(
            requests -> {
              // The container's error path renders Problem Details for requests that already
              // failed; denying it would replace their status with 401.
              requests.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
              requests.requestMatchers(HttpMethod.GET, "/api/v1/meta").permitAll();
              requests.requestMatchers(HttpMethod.POST, "/api/v1/auth/register").permitAll();
              requests.requestMatchers("/actuator/health", "/actuator/health/**").permitAll();
              if (apiDocsEnabled) {
                // API documentation is public in development only (AUTH-8, ADR-014).
                requests
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll();
              }
              // Admin endpoints also carry method security (AUTH-8).
              requests.requestMatchers("/api/v1/admin/**").hasRole("ADMIN");
              requests.anyRequest().authenticated();
            })
        // A stateless API with bearer tokens needs no CSRF protection (AUTH-9).
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .exceptionHandling(
            exceptions ->
                exceptions.authenticationEntryPoint(problems).accessDeniedHandler(problems));
    return http.build();
  }
}
