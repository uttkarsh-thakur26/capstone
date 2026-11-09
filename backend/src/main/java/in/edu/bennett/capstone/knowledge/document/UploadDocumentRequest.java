package in.edu.bennett.capstone.knowledge.document;

import in.edu.bennett.capstone.knowledge.common.PlainText;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * The {@code metadata} part of {@code POST /documents}, sent as JSON next to the file (DOC-1,
 * ADR-031). Without grants, only the owner and admins can read the new document (ACL-5). The upload
 * time is never part of it: the server records it (ADR-040).
 */
public record UploadDocumentRequest(
    @Schema(
            requiredMode = RequiredMode.REQUIRED,
            minLength = DocumentTitle.MIN_LENGTH,
            maxLength = DocumentTitle.MAX_LENGTH,
            description =
                "Stored stripped, with runs of white space as one space; at least 3 letters.")
        @NotNull(message = "must not be blank")
        @DocumentTitle
        @PlainText
        String title,
    @Schema(maxLength = 2000) @Size(max = 2000) @PlainText String description,
    DocumentCategory category,
    @Schema(
            description =
                "The date the content takes effect, if it has one: from 2000-01-01 to one year from today.")
        @DocumentDate
        LocalDate documentDate,
    @Size(max = 100) List<@Valid @NotNull GrantRequest> grants) {

  DocumentMetadata metadata() {
    return new DocumentMetadata(title, description, category, documentDate);
  }
}
