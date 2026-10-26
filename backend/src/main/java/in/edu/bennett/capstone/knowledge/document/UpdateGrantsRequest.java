package in.edu.bennett.capstone.knowledge.document;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Body of {@code PUT /documents/{id}/grants}: the complete new set (DOC-8). Grants left out are
 * removed; an empty list leaves the document to its owner and admins (ACL-5).
 */
public record UpdateGrantsRequest(
    @Schema(requiredMode = RequiredMode.REQUIRED) @NotNull @Size(max = 100)
        List<@Valid @NotNull GrantRequest> grants) {}
