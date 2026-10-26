package in.edu.bennett.capstone.knowledge.storage;

import java.io.InputStream;

/**
 * Where uploaded files are kept (D8, specification 4.4). Keys are generated here, so a user's
 * filename never becomes part of a path (DOC-2, SEC-4). Every failure of the storage itself is a
 * {@link StorageUnavailableException}, which answers {@code 503} (15.1).
 */
public interface FileStorage {

  /** Stores the content under a new key and returns the key. A half-written file is never kept. */
  String store(InputStream content);

  /** Opens a stored file. The caller closes the stream. */
  InputStream open(String key);

  /** Deletes a stored file. A file that is already gone counts as deleted. */
  void delete(String key);
}
