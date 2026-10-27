package in.edu.bennett.capstone.knowledge.ingestion;

import java.nio.file.Path;
import java.util.List;

/** Reads the text of a stored file page by page (ING-4, specification 4.4). */
interface TextExtractor {

  /**
   * @throws IngestionFailure when the file cannot give usable text
   */
  Extracted extract(Path file);

  /**
   * @param pageCount every page of the file, with or without text
   * @param pages the pages that have text, in order
   */
  record Extracted(int pageCount, List<PageText> pages) {}
}
