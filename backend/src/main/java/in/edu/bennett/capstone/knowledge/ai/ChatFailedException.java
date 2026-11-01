package in.edu.bennett.capstone.knowledge.ai;

/**
 * The chat model gave no answer: it is not configured, timed out, refused the request or failed,
 * after the provider's own retry (RAG-6).
 */
public class ChatFailedException extends RuntimeException {

  public ChatFailedException(String message, Throwable cause) {
    super(message, cause);
  }

  public ChatFailedException(String message) {
    super(message);
  }
}
