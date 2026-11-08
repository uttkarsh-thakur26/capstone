package in.edu.bennett.capstone.knowledge.assistant;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Body of {@code POST /documents/compare} (15.2): the earlier or reference document on the left,
 * the other on the right. Both must be readable and indexed (CMP-1).
 */
public record ComparisonRequest(
    @Schema(requiredMode = RequiredMode.REQUIRED) @NotNull UUID leftId,
    @Schema(requiredMode = RequiredMode.REQUIRED) @NotNull UUID rightId) {

  @Schema(hidden = true)
  @AssertTrue(message = "must name two different documents")
  public boolean isTwoDocuments() {
    return leftId == null || !leftId.equals(rightId);
  }
}
