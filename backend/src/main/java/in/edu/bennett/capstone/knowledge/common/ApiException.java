package in.edu.bennett.capstone.knowledge.common;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * A failure that the client should see, with its status and a stable code (specification 15.1). The
 * message is the Problem Details {@code detail}, so it must never contain internal data. {@link
 * ApiExceptionHandler} maps every subclass in one place (24.4).
 */
public class ApiException extends RuntimeException {

  private final HttpStatus status;
  private final String code;
  private final Map<String, Object> properties;

  public ApiException(HttpStatus status, String code, String detail) {
    this(status, code, detail, Map.of());
  }

  /**
   * @param properties extra members of the Problem Details body, for example the id of the document
   *     that a duplicate upload matches. Only values the client may see.
   */
  public ApiException(
      HttpStatus status, String code, String detail, Map<String, Object> properties) {
    super(detail);
    this.status = status;
    this.code = code;
    this.properties = Map.copyOf(properties);
  }

  public HttpStatus status() {
    return status;
  }

  public String code() {
    return code;
  }

  public Map<String, Object> properties() {
    return properties;
  }
}
