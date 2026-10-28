package in.edu.bennett.capstone.knowledge.retrieval;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Retrieval settings (specification 18, {@code app.retrieval}; RET-9). The similarity floor and the
 * default mode of question answering join this group in Phase 5, which uses them (ADR-035).
 *
 * @param k how many results a search returns when the request names no number
 */
@Validated
@ConfigurationProperties("app.retrieval")
public record RetrievalProperties(@DefaultValue("6") @Min(1) @Max(SearchRequest.MAX_K) int k) {}
