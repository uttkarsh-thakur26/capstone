package in.edu.bennett.capstone.knowledge.document;

import java.time.LocalDate;

/**
 * The editable description of a document (DOC-1, DOC-7), normalized: the title is stripped with
 * runs of white space made one space ({@link DocumentTitle.Validator#normalize}), the description
 * stripped, and a blank description stored as null.
 */
record DocumentMetadata(
    String title, String description, DocumentCategory category, LocalDate documentDate) {

  DocumentMetadata {
    title = DocumentTitle.Validator.normalize(title);
    description = blankToNull(description);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
