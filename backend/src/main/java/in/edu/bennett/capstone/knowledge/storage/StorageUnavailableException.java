package in.edu.bennett.capstone.knowledge.storage;

import in.edu.bennett.capstone.knowledge.common.ApiException;
import org.springframework.http.HttpStatus;

/** File storage failed. The cause is logged where it happens and never sent to the client. */
public class StorageUnavailableException extends ApiException {

  public StorageUnavailableException() {
    super(
        HttpStatus.SERVICE_UNAVAILABLE,
        "STORAGE_UNAVAILABLE",
        "File storage is not available. Try again later.");
  }
}
