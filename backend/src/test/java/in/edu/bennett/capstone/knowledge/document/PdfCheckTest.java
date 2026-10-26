package in.edu.bennett.capstone.knowledge.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import in.edu.bennett.capstone.knowledge.TestPdfs;
import in.edu.bennett.capstone.knowledge.common.ApiException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;

/** DOC-1 and ADR-031: the content decides, never the name. */
class PdfCheckTest {

  /** The start of a PNG file: what an image renamed to .pdf contains. */
  private static final byte[] PNG = {
    (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0, 0, 13, 'I', 'H', 'D', 'R'
  };

  @TempDir Path dir;

  @Test
  void aPdfPassesAndItsPagesAreCounted() throws IOException {
    assertThat(PdfCheck.pageCount(file(TestPdfs.withPages("one", "two", "three")))).isEqualTo(3);
  }

  @Test
  void anotherFormatRenamedToPdfIsRefused() throws IOException {
    assertRefused(file(PNG), HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_TYPE");
    assertRefused(
        file("Just some text.".getBytes(StandardCharsets.US_ASCII)),
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        "UNSUPPORTED_TYPE");
  }

  @Test
  void aPdfHeaderInFrontOfOtherContentIsRefused() throws IOException {
    byte[] fake = "%PDF-1.7\nthis is not a PDF".getBytes(StandardCharsets.US_ASCII);

    assertRefused(file(fake), HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_TYPE");
  }

  @Test
  void aTruncatedPdfIsRefused() throws IOException {
    byte[] pdf = TestPdfs.withPages("complete");
    byte[] half = Arrays.copyOf(pdf, pdf.length / 3);

    assertRefused(file(half), HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_TYPE");
  }

  @Test
  void anEmptyFileIsRefused() throws IOException {
    assertRefused(file(new byte[0]), HttpStatus.BAD_REQUEST, "EMPTY_FILE");
  }

  @Test
  void aPdfWithoutPagesIsRefused() throws IOException {
    assertRefused(file(TestPdfs.withPages()), HttpStatus.BAD_REQUEST, "EMPTY_FILE");
  }

  @Test
  void aPdfThatNeedsAPasswordIsRefused() throws IOException {
    assertRefused(file(TestPdfs.encrypted("user-secret")), HttpStatus.BAD_REQUEST, "PDF_ENCRYPTED");
  }

  @Test
  void aPdfWithOnlyAnOwnerPasswordOpensAndIsAccepted() throws IOException {
    assertThat(PdfCheck.pageCount(file(TestPdfs.ownerPasswordOnly()))).isEqualTo(1);
  }

  private Path file(byte[] content) throws IOException {
    return Files.write(Files.createTempFile(dir, "upload-", ".pdf"), content);
  }

  private static void assertRefused(Path file, HttpStatus status, String code) {
    assertThatThrownBy(() -> PdfCheck.pageCount(file))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.status()).isEqualTo(status);
              assertThat(e.code()).isEqualTo(code);
            });
  }
}
