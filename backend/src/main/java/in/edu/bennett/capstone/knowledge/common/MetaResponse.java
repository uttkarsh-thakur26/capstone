package in.edu.bennett.capstone.knowledge.common;

import io.swagger.v3.oas.annotations.media.Schema;

/** Public facts about the running application, served by {@code GET /api/v1/meta}. */
public record MetaResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String version) {}
