package in.edu.bennett.capstone.knowledge.document;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * The {@code metadata} part of {@code POST /documents}, sent as JSON next to the file (DOC-1,
 * ADR-031). Without grants, only the owner and admins can read the new document (ACL-5).
 */
public record UploadDocumentRequest(
    @Schema(requiredMode = RequiredMode.REQUIRED) @NotBlank @Size(max = 200) String title,
    @Size(max = 2000) String description,
    @Size(max = 100) String category,
    @Schema(description = "The date the content takes effect, if it has one.")
        LocalDate documentDate,
    @Size(max = 100) List<@Valid @NotNull GrantRequest> grants) {

  DocumentMetadata metadata() {
    return new DocumentMetadata(title, description, category, documentDate);
  }
}
