package in.edu.bennett.capstone.knowledge.assistant;

import java.util.List;
import java.util.UUID;

/**
 * A grounded answer (11.1) or an abstention, with what is stored with it.
 *
 * @param outcome {@code ANSWERED} or {@code INSUFFICIENT_EVIDENCE}; failures are an {@link
 *     AnswerFailedException}
 * @param text the validated answer, or the fixed abstention message (RAG-2)
 * @param citations empty for an abstention
 */
public record Answer(AnswerOutcome outcome, String text, List<Citation> citations, Trace trace) {

  /**
   * A cited source. Every field comes from the stored chunk, never from the model (RAG-3); it is
   * stored as a snapshot, because chunks are replaced on re-index (DATA-1).
   *
   * @param ordinal the source number, as the answer's {@code [n]} markers use it
   * @param score the chunk's similarity to the question
   */
  public record Citation(
      int ordinal,
      UUID documentId,
      String documentTitle,
      int pageStart,
      int pageEnd,
      int chunkIndex,
      String snippet,
      double score) {}

  /**
   * What happened on the way, for the stored message and the log line. No question, prompt or
   * answer text (SEC-10).
   *
   * @param retrieved how many chunks retrieval returned
   * @param topSimilarity the best chunk's similarity, null when none was retrieved
   * @param sourcesSent how many sources the prompt held; 0 when the model was not called
   * @param model the model that answered; null when it was not called or did not answer
   * @param promptVersion the system prompt sent; null when the model was not called
   * @param inputTokens billed prompt tokens, null when unknown
   * @param outputTokens billed completion tokens, reasoning included, null when unknown
   * @param cachedInputTokens prompt tokens read from the provider's cache, null when unknown
   */
  public record Trace(
      int retrieved,
      Double topSimilarity,
      int sourcesSent,
      String model,
      String promptVersion,
      Integer inputTokens,
      Integer outputTokens,
      Long cachedInputTokens,
      long retrievalMillis,
      long generationMillis) {}
}
