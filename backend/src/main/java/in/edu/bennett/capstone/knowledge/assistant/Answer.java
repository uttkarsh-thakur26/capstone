package in.edu.bennett.capstone.knowledge.assistant;

import in.edu.bennett.capstone.knowledge.ai.ChatResult;
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
   * @param historyTurns how many earlier turns went to the model (CONV-4)
   * @param rewrite the call that turned a follow-up into a standalone query (11.1 step 3); null
   *     when there was no history
   * @param retrieved how many chunks retrieval returned
   * @param topSimilarity the best chunk's similarity, null when none was retrieved
   * @param sourcesSent how many sources the prompt held; 0 when the model was not called
   * @param model the model that answered; null when it was not called or did not answer
   * @param promptVersion the system prompt sent; null when the model was not called
   * @param answer the answering call; null when the model was not called or did not answer
   */
  public record Trace(
      int historyTurns,
      Usage rewrite,
      int retrieved,
      Double topSimilarity,
      int sourcesSent,
      String model,
      String promptVersion,
      Usage answer,
      long retrievalMillis,
      long generationMillis) {

    /** Before anything was retrieved. */
    static Trace start(int historyTurns, Usage rewrite) {
      return new Trace(historyTurns, rewrite, 0, null, 0, null, null, null, 0, 0);
    }

    Trace retrieved(int count, Double top, long millis) {
      return new Trace(historyTurns, rewrite, count, top, 0, null, null, null, millis, 0);
    }

    Trace generated(int sources, String model, String version, Usage usage, long millis) {
      return new Trace(
          historyTurns,
          rewrite,
          retrieved,
          topSimilarity,
          sources,
          model,
          version,
          usage,
          retrievalMillis,
          millis);
    }
  }

  /**
   * The tokens one model call was billed for.
   *
   * @param inputTokens prompt tokens, null when unknown
   * @param outputTokens completion tokens, reasoning included, null when unknown
   * @param cachedInputTokens prompt tokens read from the provider's cache, null when unknown
   */
  public record Usage(Integer inputTokens, Integer outputTokens, Long cachedInputTokens) {

    static Usage of(ChatResult result) {
      return new Usage(result.inputTokens(), result.outputTokens(), result.cachedInputTokens());
    }
  }

  /**
   * An earlier question and its answer, as the conversation stored them (CONV-4).
   *
   * @param answer the stored answer text, with its {@code [n]} markers
   */
  public record Turn(String question, String answer) {}
}
