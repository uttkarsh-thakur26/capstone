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
 */
final class PdfCheck {

  /** The PDF header. Readers tolerate junk before it, but every real PDF starts with it. */
  private static final byte[] SIGNATURE = {'%', 'P', 'D', 'F', '-'};

  private PdfCheck() {}

  /**
   * Checks the file and returns its page count.
   *
   * @throws ApiException {@code EMPTY_FILE}, {@code UNSUPPORTED_TYPE} or {@code PDF_ENCRYPTED}
   */
  static int pageCount(Path file) {
    if (size(file) == 0) {
      throw empty("The file is empty.");
    }
    if (!startsWithSignature(file)) {
      throw notPdf();
    }
    try (PDDocument pdf = Loader.loadPDF(file.toFile())) {
      int pages = pdf.getNumberOfPages();
      if (pages == 0) {
        throw empty("The PDF has no pages.");
      }
      return pages;
    } catch (InvalidPasswordException e) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "PDF_ENCRYPTED",
          "The PDF is protected by a password. Remove the password and upload it again.");
    } catch (IOException e) {
      // PDFBox could not parse it: a damaged file, or another format behind a PDF header.
      throw notPdf();
    }
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

  private static ApiException empty(String detail) {
    return new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", detail);
  }

  private static ApiException notPdf() {
    return new ApiException(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        "UNSUPPORTED_TYPE",
        "Only PDF files can be uploaded, and this file is not a PDF that can be read.");
  }
}
