package in.edu.bennett.capstone.knowledge.document;

/**
 * Why processing failed (specification 8.3, ADR-030, ADR-034), with the message a user sees.
 * Internal details go to the log, never into the message.
 */
public enum FailureCode {
  UNSUPPORTED_TYPE("The file is not a PDF that can be read."),
  EMPTY("The file is empty."),
  ENCRYPTED("The PDF is protected by a password."),
  NO_TEXT_LAYER("The PDF has no text layer; it is probably a scanned image."),
  TOO_MANY_PAGES("The PDF has more pages than allowed."),
  TOO_MANY_CHUNKS("The PDF has more text than one document may hold."),
  EXTRACTION_ERROR("The text of the PDF could not be extracted."),
  EMBEDDING_ERROR("The document could not be indexed because the embedding service failed."),
  PROCESSING_TIMEOUT("Processing was interrupted too many times."),
  INTERNAL("An unexpected error stopped processing.");

  private final String message;

  FailureCode(String message) {
    this.message = message;
  }

  public String message() {
    return message;
  }
}
