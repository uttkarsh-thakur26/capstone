package in.edu.bennett.capstone.knowledge.document;

import java.time.LocalDate;

/**
 * The editable description of a document (DOC-1, DOC-7), normalized: text is stripped, and blank
 * optional text is stored as null.
 */
record DocumentMetadata(String title, String description, String category, LocalDate documentDate) {

  DocumentMetadata {
    title = title.strip();
    description = blankToNull(description);
    category = blankToNull(category);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
