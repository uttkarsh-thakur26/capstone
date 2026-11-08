package in.edu.bennett.capstone.knowledge.retrieval;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Retrieval settings (specification 18, {@code app.retrieval}; RET-9).
 *
 * @param k how many results a search returns when the request names no number, and how many chunks
 *     a question retrieves
 * @param similarityFloor the evidence gate (11.1 step 5): when no chunk retrieved for a question is
 *     at least this similar to it, the answer is insufficient evidence and the chat model is not
 *     called. It is a cosine similarity in every mode, never a fused score (ADR-040), and belongs
 *     to the embedding model: 0.30 is provisional for qwen3-embedding:0.6b, between the off-topic
 *     questions measured (at most 0.282) and the relevant ones (at least 0.336), until the
 *     calibration of Phase 7 (RAG-8, ADR-037)
 * @param assistantMode how a question retrieves its chunks: {@code HYBRID} by default, {@code
 *     SEMANTIC} for the evaluation's comparison (20.4). Not {@code KEYWORD}, which reports no
 *     similarity for the evidence gate
 * @param hybridCandidates how many chunks each ranking of a hybrid search contributes before they
 *     are fused; at least {@code k} are always taken, so a hybrid search still returns {@code k}
 *     results (RET-4)
 */
@Validated
@ConfigurationProperties("app.retrieval")
public record RetrievalProperties(
    @DefaultValue("6") @Min(1) @Max(SearchRequest.MAX_K) int k,
    @DefaultValue("0.30") @DecimalMin("-1.0") @DecimalMax("1.0") double similarityFloor,
    @DefaultValue("HYBRID") @NotNull SearchMode assistantMode,
    @DefaultValue("50") @Min(1) @Max(200) int hybridCandidates) {

  public RetrievalProperties {
    if (assistantMode == SearchMode.KEYWORD) {
      throw new IllegalArgumentException(
          "app.retrieval.assistant-mode must be HYBRID or SEMANTIC: keyword search reports no"
              + " similarity for the evidence gate");
    }
  }
}
