package in.edu.bennett.capstone.knowledge.common;

import org.springframework.http.HttpStatus;

/**
 * A failure that the client should see, with its status and a stable code (specification 15.1). The
 * message is the Problem Details {@code detail}, so it must never contain internal data. {@link
 * ApiExceptionHandler} maps every subclass in one place (24.4).
 */
public class ApiException extends RuntimeException {

  private final HttpStatus status;
  private final String code;

  public ApiException(HttpStatus status, String code, String detail) {
    super(detail);
    this.status = status;
    this.code = code;
  }

  public HttpStatus status() {
    return status;
  }

  public String code() {
    return code;
  }
}
