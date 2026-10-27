package in.edu.bennett.capstone.knowledge.ai;

/** The embedding model could not embed a text, after retries where the failure was transient. */
public class EmbeddingFailedException extends RuntimeException {

  public EmbeddingFailedException(String message, Throwable cause) {
    super(message, cause);
  }

  public EmbeddingFailedException(String message) {
    super(message);
  }
}
