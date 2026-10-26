package in.edu.bennett.capstone.knowledge.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Files on a local directory, one file per key (D8). Keys are random UUIDs, and a key in any other
 * form is refused before it touches the file system, so no key can point outside the root.
 */
public class LocalFileStorage implements FileStorage {

  private static final Logger log = LoggerFactory.getLogger(LocalFileStorage.class);

  private static final Pattern KEY =
      Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

  private final Path root;

  /** Creates the directory if needed and refuses to start when it cannot be written. */
  public LocalFileStorage(Path root) {
    this.root = root.toAbsolutePath().normalize();
    try {
      Files.createDirectories(this.root);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot create the storage directory " + this.root, e);
    }
    if (!Files.isWritable(this.root)) {
      throw new IllegalStateException("The storage directory " + this.root + " is not writable");
    }
  }

  @Override
  public String store(InputStream content) {
    String key = UUID.randomUUID().toString();
    Path target = resolve(key);
    // Written under a temporary name first and renamed when complete, so a crash or a failed copy
    // never leaves a partial file under a real key.
    Path partial = root.resolve(key + ".part");
    try {
      Files.copy(content, partial);
      Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE);
      return key;
    } catch (IOException e) {
      log.error("Could not store a file under key {}", key, e);
      deleteQuietly(partial);
      throw new StorageUnavailableException();
    }
  }

  @Override
  public InputStream open(String key) {
    Path file = resolve(key);
    try {
      return Files.newInputStream(file);
    } catch (NoSuchFileException e) {
      log.error("Stored file {} is missing", key);
      throw new StorageUnavailableException();
    } catch (IOException e) {
      log.error("Could not open stored file {}", key, e);
      throw new StorageUnavailableException();
    }
  }

  @Override
  public void delete(String key) {
    try {
      Files.deleteIfExists(resolve(key));
    } catch (IOException e) {
      log.warn("Could not delete stored file {}", key, e);
      throw new StorageUnavailableException();
    }
  }

  private Path resolve(String key) {
    if (key == null || !KEY.matcher(key).matches()) {
      throw new IllegalArgumentException("Not a storage key: " + key);
    }
    return root.resolve(key);
  }

  private static void deleteQuietly(Path file) {
    try {
      Files.deleteIfExists(file);
    } catch (IOException e) {
      log.warn("Could not remove the partial file {}", file.getFileName(), e);
    }
  }
}
