package in.edu.bennett.capstone.knowledge.common;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;

/**
 * The shape of every error response, as the OpenAPI document describes it (specification 15.1). At
 * runtime the body is Spring's {@code ProblemDetail}; this record exists so that the SPA's types
 * are generated, not written by hand (16.1). {@code ProblemDetailsTest} checks that real responses
 * use no other fields. {@code errors} is present only when {@code code} is {@code
 * VALIDATION_FAILED}.
 */
@Schema(name = "ProblemResponse", description = "RFC 9457 Problem Details with a stable code")
public record ProblemResponse(
    @Schema(requiredMode = RequiredMode.REQUIRED, example = "about:blank") String type,
    @Schema(requiredMode = RequiredMode.REQUIRED) String title,
    @Schema(requiredMode = RequiredMode.REQUIRED) int status,
    @Schema(requiredMode = RequiredMode.REQUIRED) String detail,
    String instance,
    @Schema(requiredMode = RequiredMode.REQUIRED, example = "VALIDATION_FAILED") String code,
    List<FieldErrorResponse> errors,
    @Schema(
            description =
                "With 429 RATE_LIMITED: whole seconds until the request is accepted again, as in"
                    + " the Retry-After header (SEC-8).")
        Long retryAfterSeconds) {}
