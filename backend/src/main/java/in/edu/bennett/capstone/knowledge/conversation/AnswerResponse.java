package in.edu.bennett.capstone.knowledge.conversation;

import in.edu.bennett.capstone.knowledge.assistant.AnswerOutcome;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;
import java.util.UUID;

/**
 * The answer response of specification 15.3. For insufficient evidence, {@code answer} is the fixed
 * message and {@code citations} is empty (RAG-2).
 *
 * @param messageId the stored answer
 * @param answer the answer with its {@code [n]} markers, which match the citations' ordinals
 */
public record AnswerResponse(
    @Schema(requiredMode = RequiredMode.REQUIRED) UUID messageId,
    @Schema(requiredMode = RequiredMode.REQUIRED) AnswerOutcome outcome,
    @Schema(requiredMode = RequiredMode.REQUIRED) String answer,
    @Schema(requiredMode = RequiredMode.REQUIRED) List<CitationResponse> citations) {}
