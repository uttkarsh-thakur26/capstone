package in.edu.bennett.capstone.knowledge.document;

import in.edu.bennett.capstone.knowledge.common.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.springframework.http.HttpStatus;

/**
 * DOC-1: whether a file is a PDF is decided by its content, never by its name or the client's
 * header (ADR-031). The file must start with the PDF signature and PDFBox must open it. A file that
 * needs a password to open is refused; one with only an owner password opens and is accepted.
 *
 * <p>Upload and ingestion run the same check (ADR-030): upload answers with an error, ingestion
 * fails the document with the matching code when the stored file no longer passes it.
 */
public final class PdfCheck {

  /** The PDF header. Readers tolerate junk before it, but every real PDF starts with it. */
  private static final byte[] SIGNATURE = {'%', 'P', 'D', 'F', '-'};

  private PdfCheck() {}

  /**
   * The result of the check.
   *
   * @param failure why the file is refused, or {@code null} when it passes
   * @param detail what is wrong, for the uploader
   * @param pageCount the number of pages when the file passes, otherwise 0
   */
  public record Inspection(FailureCode failure, String detail, int pageCount) {

    public boolean passed() {
      return failure == null;
    }
  }

  /** Checks the file without throwing for a file that fails. */
  public static Inspection inspect(Path file) {
    if (size(file) == 0) {
      return refused(FailureCode.EMPTY, "The file is empty.");
    }
    if (!startsWithSignature(file)) {
      return refused(FailureCode.UNSUPPORTED_TYPE, "The file is not a PDF.");
    }
    try (PDDocument pdf = Loader.loadPDF(file.toFile())) {
      int pages = pdf.getNumberOfPages();
      if (pages == 0) {
        return refused(FailureCode.EMPTY, "The PDF has no pages.");
      }
      return new Inspection(null, null, pages);
    } catch (InvalidPasswordException e) {
      return refused(FailureCode.ENCRYPTED, "The PDF is protected by a password.");
    } catch (IOException e) {
      // PDFBox could not parse it: a damaged file, or another format behind a PDF header.
      return refused(FailureCode.UNSUPPORTED_TYPE, "PDFBox could not open the file.");
    }
  }

  /**
   * Checks an upload and returns its page count.
   *
   * @throws ApiException {@code EMPTY_FILE}, {@code UNSUPPORTED_TYPE} or {@code PDF_ENCRYPTED}
   */
  static int pageCount(Path file) {
    Inspection inspection = inspect(file);
    if (inspection.passed()) {
      return inspection.pageCount();
    }
    throw switch (inspection.failure()) {
      case EMPTY -> new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", inspection.detail());
      case ENCRYPTED ->
          new ApiException(
              HttpStatus.BAD_REQUEST,
              "PDF_ENCRYPTED",
              "The PDF is protected by a password. Remove the password and upload it again.");
      default ->
          new ApiException(
              HttpStatus.UNSUPPORTED_MEDIA_TYPE,
              "UNSUPPORTED_TYPE",
              "Only PDF files can be uploaded, and this file is not a PDF that can be read.");
    };
  }

  private static Inspection refused(FailureCode failure, String detail) {
    return new Inspection(failure, detail, 0);
  }

  private static boolean startsWithSignature(Path file) {
    try (InputStream in = Files.newInputStream(file)) {
      return Arrays.equals(in.readNBytes(SIGNATURE.length), SIGNATURE);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static long size(Path file) {
    try {
      return Files.size(file);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
