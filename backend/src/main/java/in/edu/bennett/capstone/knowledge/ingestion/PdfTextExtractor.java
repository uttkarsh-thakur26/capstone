package in.edu.bennett.capstone.knowledge.ingestion;

import in.edu.bennett.capstone.knowledge.document.FailureCode;
import in.edu.bennett.capstone.knowledge.document.PdfCheck;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

/**
 * ING-4: PDF text through Spring AI's {@link PagePdfDocumentReader}, one unit per page, with the
 * page number the reader records. The stored file first passes the same check as an upload
 * (ADR-030), so a file that changed in storage fails with {@code EMPTY}, {@code UNSUPPORTED_TYPE}
 * or {@code ENCRYPTED}. A file with almost no text is probably a scan: {@code NO_TEXT_LAYER} (OCR
 * is T3).
 */
@Component
class PdfTextExtractor implements TextExtractor {

  private final IngestionProperties properties;

  PdfTextExtractor(IngestionProperties properties) {
    this.properties = properties;
  }

  @Override
  public Extracted extract(Path file) {
    PdfCheck.Inspection inspection = PdfCheck.inspect(file);
    if (!inspection.passed()) {
      throw new IngestionFailure(inspection.failure(), null, inspection.detail());
    }
    int pageCount = inspection.pageCount();
    if (pageCount > properties.maxPages()) {
      throw new IngestionFailure(
          FailureCode.TOO_MANY_PAGES,
          "The PDF has "
              + pageCount
              + " pages; at most "
              + properties.maxPages()
              + " can be processed.",
          pageCount + " pages");
    }
    List<PageText> pages = read(file);
    long characters =
        pages.stream()
            .mapToLong(
                page -> page.text().codePoints().filter(c -> !Character.isWhitespace(c)).count())
            .sum();
    if (characters < (long) properties.minCharsPerPage() * pageCount) {
      throw new IngestionFailure(
          FailureCode.NO_TEXT_LAYER, null, characters + " characters on " + pageCount + " pages");
    }
    return new Extracted(pageCount, pages);
  }

  private static List<PageText> read(Path file) {
    try (ClosingReader reader = new ClosingReader(file)) {
      List<PageText> pages = new ArrayList<>();
      for (Document page : reader.get()) {
        Object number = page.getMetadata().get(PagePdfDocumentReader.METADATA_START_PAGE_NUMBER);
        pages.add(new PageText(((Number) number).intValue(), page.getText()));
      }
      return pages;
    } catch (RuntimeException | IOException | StackOverflowError e) {
      // A damaged or hostile file can fail deep inside PDFBox, even with a stack overflow.
      throw new IngestionFailure(FailureCode.EXTRACTION_ERROR, null, e.toString(), e);
    }
  }

  /** The reader parses the file when it is created and never closes it; this closes it. */
  private static final class ClosingReader extends PagePdfDocumentReader implements AutoCloseable {

    ClosingReader(Path file) {
      super(
          new FileSystemResource(file),
          PdfDocumentReaderConfig.builder().withPagesPerDocument(1).build());
    }

    @Override
    public void close() throws IOException {
      document.close();
    }
  }
}
