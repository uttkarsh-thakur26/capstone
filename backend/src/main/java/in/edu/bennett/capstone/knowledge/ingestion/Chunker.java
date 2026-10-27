package in.edu.bennett.capstone.knowledge.ingestion;

import java.util.List;

/** Splits a document's text into chunks (ING-6, D10, specification 4.4). */
interface Chunker {

  /**
   * @param pages the normalized pages of one document, in order
   * @return the chunks in order, never empty text, each with its page range
   */
  List<Chunk> split(List<PageText> pages);

  /**
   * One chunk.
   *
   * @param index the position in the document, from 0
   * @param pageStart the first page any of its text comes from
   * @param pageEnd the last page; equal to {@code pageStart} unless the chunk spans pages
   * @param tokenCount counted with the one tokenizer of ING-6
   */
  record Chunk(int index, int pageStart, int pageEnd, String content, int tokenCount) {}
}
