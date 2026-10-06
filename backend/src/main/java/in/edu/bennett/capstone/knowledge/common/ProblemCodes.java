package in.edu.bennett.capstone.knowledge.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/** The {@code code} of a Problem Details response that no domain exception named. */
final class ProblemCodes {

  static final String VALIDATION_FAILED = "VALIDATION_FAILED";
  static final String UNAUTHENTICATED = "UNAUTHENTICATED";
  static final String FORBIDDEN = "FORBIDDEN";
  static final String NOT_FOUND = "NOT_FOUND";
  static final String INTERNAL = "INTERNAL";

  private ProblemCodes() {}

  static String forStatus(HttpStatusCode status) {
    HttpStatus known = HttpStatus.resolve(status.value());
    if (known == null) {
      return status.is5xxServerError() ? INTERNAL : "ERROR";
    }
    return switch (known) {
      case UNAUTHORIZED -> UNAUTHENTICATED;
      case INTERNAL_SERVER_ERROR -> INTERNAL;
      default -> known.name();
    };
  }
}
