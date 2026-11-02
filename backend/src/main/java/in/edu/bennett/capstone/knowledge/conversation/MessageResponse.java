package in.edu.bennett.capstone.knowledge.conversation;

import in.edu.bennett.capstone.knowledge.assistant.AnswerOutcome;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A question or an answer of a conversation (specification 15.2).
 *
 * @param content the question, or the stored answer text, which stays visible to its author even
 *     when a citation is no longer available (CONV-6)
 * @param outcome answers only
 * @param citations empty for questions
 */
public record MessageResponse(
    @Schema(requiredMode = RequiredMode.REQUIRED) UUID id,
    @Schema(requiredMode = RequiredMode.REQUIRED) MessageRole role,
    @Schema(requiredMode = RequiredMode.REQUIRED) String content,
    AnswerOutcome outcome,
    @Schema(requiredMode = RequiredMode.REQUIRED) Instant createdAt,
    @Schema(requiredMode = RequiredMode.REQUIRED) List<CitationResponse> citations) {}
