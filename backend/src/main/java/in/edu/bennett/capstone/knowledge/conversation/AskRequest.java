package in.edu.bennett.capstone.knowledge.conversation;

import in.edu.bennett.capstone.knowledge.common.PlainText;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Body of {@code POST /conversations/{id}/messages} (specification 15.2, 11.1 step 2).
 *
 * @param question what to ask. 1,000 characters, as for a search query, stay far below the
 *     embedding model's input limit
 * @param documentIds answer only from these documents; each must be readable, or the answer is 404
 *     (CONV-7, RET-8)
 */
public record AskRequest(
    @Schema(requiredMode = RequiredMode.REQUIRED) @NotBlank @Size(max = 1000) @PlainText
        String question,
    @Size(max = 100) List<@NotNull UUID> documentIds) {}
