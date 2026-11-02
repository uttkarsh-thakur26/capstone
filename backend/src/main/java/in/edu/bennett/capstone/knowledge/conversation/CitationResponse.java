package in.edu.bennett.capstone.knowledge.conversation;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.UUID;

/**
 * A cited passage. Title, pages and snippet come from the stored chunk, never from the model
 * (RAG-3). When the document was deleted or the user can no longer read it, the citation is
 * unavailable and says nothing about the document: no id, title, pages or snippet (CONV-6).
 *
 * @param ordinal the source number, as the answer's {@code [n]} markers use it
 * @param available false when the document was deleted or is no longer readable
 */
public record CitationResponse(
    @Schema(requiredMode = RequiredMode.REQUIRED) int ordinal,
    @Schema(requiredMode = RequiredMode.REQUIRED) boolean available,
    UUID documentId,
    String documentTitle,
    Integer pageStart,
    Integer pageEnd,
    String snippet) {

  static CitationResponse available(
      int ordinal, UUID documentId, String title, int pageStart, int pageEnd, String snippet) {
    return new CitationResponse(ordinal, true, documentId, title, pageStart, pageEnd, snippet);
  }

  static CitationResponse unavailable(int ordinal) {
    return new CitationResponse(ordinal, false, null, null, null, null, null);
  }
}
