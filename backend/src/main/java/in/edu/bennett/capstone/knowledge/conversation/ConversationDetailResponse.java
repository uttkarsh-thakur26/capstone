package in.edu.bennett.capstone.knowledge.conversation;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A conversation with its messages, oldest first (CONV-2).
 *
 * @param title null until the first question names it
 */
public record ConversationDetailResponse(
    @Schema(requiredMode = RequiredMode.REQUIRED) UUID id,
    String title,
    @Schema(requiredMode = RequiredMode.REQUIRED) Instant createdAt,
    @Schema(requiredMode = RequiredMode.REQUIRED) Instant updatedAt,
    @Schema(requiredMode = RequiredMode.REQUIRED) List<MessageResponse> messages) {}
