package in.edu.bennett.capstone.knowledge.retrieval;

import in.edu.bennett.capstone.knowledge.common.PlainText;
import in.edu.bennett.capstone.knowledge.document.DocumentCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Body of {@code POST /search} (specification 15.2).
 *
 * @param query what to look for. A query of 1,000 characters stays far below the embedding model's
 *     input limit, prefix included
 * @param documentIds search only these documents. Each must be readable, or the answer is 404
 *     (RET-8)
 * @param category search only documents of this category
 * @param k how many results at most; {@code app.retrieval.k} when absent (RET-9)
 */
public record SearchRequest(
    @Schema(requiredMode = RequiredMode.REQUIRED) @NotBlank @Size(max = 1000) @PlainText
        String query,
    @Schema(requiredMode = RequiredMode.REQUIRED) @NotNull SearchMode mode,
    @Size(max = 100) List<@NotNull UUID> documentIds,
    DocumentCategory category,
    @Min(1) @Max(SearchRequest.MAX_K) Integer k) {

  /** The most results one search returns. */
  public static final int MAX_K = 50;
}
