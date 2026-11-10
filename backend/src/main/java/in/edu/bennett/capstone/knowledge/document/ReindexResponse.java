package in.edu.bennett.capstone.knowledge.document;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/**
 * The answer to {@code POST /admin/reindex}.
 *
 * @param queued documents sent back for processing: every indexed or failed one, archived included
 * @param skipped documents already waiting or being processed, which the worker takes anyway
 */
public record ReindexResponse(
    @Schema(requiredMode = RequiredMode.REQUIRED) int queued,
    @Schema(requiredMode = RequiredMode.REQUIRED) int skipped) {}
