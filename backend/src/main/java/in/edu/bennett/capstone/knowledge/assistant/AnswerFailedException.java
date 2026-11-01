package in.edu.bennett.capstone.knowledge.assistant;

/**
 * A model failed or gave malformed output, so the question ends with the {@code ERROR} outcome
 * (RAG-6). Carries the trace, because a malformed answer was still billed.
 */
public class AnswerFailedException extends RuntimeException {

  private final transient Answer.Trace trace;

  AnswerFailedException(String message, Answer.Trace trace, Throwable cause) {
    super(message, cause);
    this.trace = trace;
  }

  public Answer.Trace trace() {
    return trace;
  }
}
