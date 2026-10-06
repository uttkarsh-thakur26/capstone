package in.edu.bennett.capstone.knowledge.common;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Top-level application settings (specification 18).
 *
 * @param displayName the product name shown to users. It has no default, so the application does
 *     not start without it (D1, ADR-007).
 */
@Validated
@ConfigurationProperties("app")
public record AppProperties(@NotBlank String displayName) {}
