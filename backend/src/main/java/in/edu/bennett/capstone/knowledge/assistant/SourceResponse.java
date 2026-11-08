package in.edu.bennett.capstone.knowledge.assistant;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.UUID;

/**
 * A cited passage of a summary or a comparison, in the same shape as an answer's citation, so the
 * screens show it with the same source chip and panel. Title, pages and snippet come from the
 * stored chunks, never from the model (RAG-3). It is sent only after a fresh access check of its
 * document, so it is always available.
 *
 * @param ordinal the number its {@code [n]} markers use
 */
public record SourceResponse(
    @Schema(requiredMode = RequiredMode.REQUIRED) int ordinal,
    @Schema(requiredMode = RequiredMode.REQUIRED) boolean available,
    UUID documentId,
    String documentTitle,
    Integer pageStart,
    Integer pageEnd,
    String snippet) {

  static SourceResponse of(
      int ordinal, UUID documentId, String title, int pageStart, int pageEnd, String snippet) {
    return new SourceResponse(ordinal, true, documentId, title, pageStart, pageEnd, snippet);
  }
}
