package in.edu.bennett.capstone.knowledge.retrieval;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Retrieval settings (specification 18, {@code app.retrieval}; RET-9). There is no mode setting
 * yet: question answering retrieves semantically, the only mode whose score the floor can be
 * compared with, until hybrid search arrives in Phase 8 (ADR-037).
 *
 * @param k how many results a search returns when the request names no number, and how many chunks
 *     a question retrieves
 * @param similarityFloor the evidence gate (11.1 step 5): when the best chunk for a question is
 *     less similar than this, the answer is insufficient evidence and the chat model is not called.
 *     It belongs to the embedding model: provisional until the calibration of Phase 7 (RAG-8)
 */
@Validated
@ConfigurationProperties("app.retrieval")
public record RetrievalProperties(
    @DefaultValue("6") @Min(1) @Max(SearchRequest.MAX_K) int k,
    @DefaultValue("0.35") @DecimalMin("-1.0") @DecimalMax("1.0") double similarityFloor) {}
