package in.edu.bennett.capstone.knowledge.assistant;

/** How a question ended (specification 7.2, {@code message.outcome}). */
public enum AnswerOutcome {
  /** Answered from the sources, with at least one valid citation. */
  ANSWERED,
  /** The sources do not answer it (RAG-2). Never says why. */
  INSUFFICIENT_EVIDENCE,
  /** A model failed or gave malformed output (RAG-6). */
  ERROR
}
