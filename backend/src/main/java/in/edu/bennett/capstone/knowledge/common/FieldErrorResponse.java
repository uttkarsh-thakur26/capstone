package in.edu.bennett.capstone.knowledge.common;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/** One entry of the {@code errors} list of a validation problem (specification 15.1). */
@Schema(description = "One invalid field of the request")
public record FieldErrorResponse(
    @Schema(requiredMode = RequiredMode.REQUIRED) String field,
    @Schema(requiredMode = RequiredMode.REQUIRED) String message) {}
