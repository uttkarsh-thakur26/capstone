package in.edu.bennett.capstone.knowledge.document;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;

/**
 * Every grant of a document (DOC-8): role grants first, then people. At most 100, so the list is
 * not paged.
 */
public record GrantsResponse(
    @Schema(requiredMode = RequiredMode.REQUIRED) List<GrantResponse> grants) {}
