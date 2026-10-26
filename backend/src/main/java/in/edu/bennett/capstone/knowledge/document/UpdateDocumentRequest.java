package in.edu.bennett.capstone.knowledge.document;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Body of {@code PATCH /documents/{id}} (DOC-7, DOC-11, ADR-031). With {@code title}, the four
 * metadata fields are replaced together, so an omitted optional field is cleared; the edit form
 * always sends all of them. With {@code archived}, the document is archived or brought back. A
 * request may do both.
 */
public record UpdateDocumentRequest(
    @Schema(description = "Required to change the metadata; replaces all four fields.")
        @Size(max = 200)
        @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank")
        String title,
    @Size(max = 2000) String description,
    @Size(max = 100) String category,
    LocalDate documentDate,
    Boolean archived) {

  @Schema(hidden = true)
  @AssertTrue(message = "must change the metadata (with a title), archived, or both")
  public boolean isAnyChange() {
    return title != null || archived != null;
  }

  @Schema(hidden = true)
  @AssertTrue(message = "a title is required to change the description, category or date")
  public boolean isMetadataComplete() {
    return title != null || (description == null && category == null && documentDate == null);
  }

  /** The new metadata, or null when the request does not change it. */
  DocumentMetadata metadata() {
    return title == null ? null : new DocumentMetadata(title, description, category, documentDate);
  }
}
