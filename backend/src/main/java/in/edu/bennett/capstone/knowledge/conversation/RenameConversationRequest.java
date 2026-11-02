package in.edu.bennett.capstone.knowledge.conversation;

import in.edu.bennett.capstone.knowledge.common.PlainText;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code PATCH /conversations/{id}} (CONV-2). */
public record RenameConversationRequest(
    @Schema(requiredMode = RequiredMode.REQUIRED) @NotBlank @Size(max = 200) @PlainText
        String title) {}
