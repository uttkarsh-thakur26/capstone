package in.edu.bennett.capstone.knowledge.ingestion;

import in.edu.bennett.capstone.knowledge.document.FailureCode;

/**
 * A document cannot be indexed, for a reason a user can be told (8.3). {@code userMessage}, when
 * set, replaces the code's own message and never holds internal details.
 */
class IngestionFailure extends RuntimeException {

  private final FailureCode code;
  private final String userMessage;

  IngestionFailure(FailureCode code, String userMessage, String internalDetail) {
    super(code + ": " + internalDetail);
    this.code = code;
    this.userMessage = userMessage;
  }

  IngestionFailure(FailureCode code, String userMessage, String internalDetail, Throwable cause) {
    super(code + ": " + internalDetail, cause);
    this.code = code;
    this.userMessage = userMessage;
  }

  FailureCode code() {
    return code;
  }

  String userMessage() {
    return userMessage;
  }
}
