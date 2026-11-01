package in.edu.bennett.capstone.knowledge.conversation;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.time.Instant;
import java.util.UUID;

/**
 * A conversation (specification 15.2).
 *
 * @param title the first question, shortened (CONV-3); null until a question is asked
 */
public record ConversationResponse(
    @Schema(requiredMode = RequiredMode.REQUIRED) UUID id,
    String title,
    @Schema(requiredMode = RequiredMode.REQUIRED) Instant createdAt,
    @Schema(requiredMode = RequiredMode.REQUIRED) Instant updatedAt) {

  static ConversationResponse of(Conversation conversation) {
    return new ConversationResponse(
        conversation.getId(),
        conversation.getTitle(),
        conversation.getCreatedAt(),
        conversation.getUpdatedAt());
  }
}
