package in.edu.bennett.capstone.knowledge.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** D8, DOC-2, SEC-4: generated keys, no user-controlled paths, no partial files. */
class LocalFileStorageTest {

  @TempDir Path root;

  @Test
  void storesUnderAGeneratedKeyAndReadsTheSameBytesBack() throws IOException {
    LocalFileStorage storage = new LocalFileStorage(root);
    byte[] content = "%PDF-1.7 test".getBytes(StandardCharsets.US_ASCII);

    String key = storage.store(new ByteArrayInputStream(content));

    assertThat(UUID.fromString(key)).hasToString(key);
    assertThat(root.resolve(key)).hasBinaryContent(content);
    try (InputStream in = storage.open(key)) {
      assertThat(in.readAllBytes()).isEqualTo(content);
    }
  }

  @Test
  void deletesAndTreatsAMissingFileAsDeleted() {
    LocalFileStorage storage = new LocalFileStorage(root);
    String key = storage.store(new ByteArrayInputStream(new byte[] {1, 2, 3}));

    storage.delete(key);
    storage.delete(key);

    assertThat(root.resolve(key)).doesNotExist();
  }

  @Test
  void refusesAnyKeyItDidNotGenerate() {
    LocalFileStorage storage = new LocalFileStorage(root);

    for (String key :
        new String[] {"../outside", "..\\outside", "/etc/passwd", "a/b", "", "report.pdf"}) {
      assertThatIllegalArgumentException().isThrownBy(() -> storage.open(key));
      assertThatIllegalArgumentException().isThrownBy(() -> storage.delete(key));
    }
  }

  @Test
  void aFailedCopyLeavesNoFileBehind() throws IOException {
    LocalFileStorage storage = new LocalFileStorage(root);
    InputStream failing =
        new InputStream() {
          private int sent;

          @Override
          public int read() throws IOException {
            if (sent++ < 10) {
              return 'x';
            }
            throw new IOException("connection reset");
          }
        };

    assertThatThrownBy(() -> storage.store(failing))
        .isInstanceOf(StorageUnavailableException.class);
    try (Stream<Path> files = Files.list(root)) {
      assertThat(files).isEmpty();
    }
  }

  @Test
  void aMissingFileIsAStorageFailureNotAnInternalError() {
    LocalFileStorage storage = new LocalFileStorage(root);

    assertThatThrownBy(() -> storage.open(UUID.randomUUID().toString()))
        .isInstanceOf(StorageUnavailableException.class);
  }

  @Test
  void createsTheRootDirectory() {
    Path nested = root.resolve("a").resolve("b");

    new LocalFileStorage(nested);

    assertThat(nested).isDirectory();
  }
}
