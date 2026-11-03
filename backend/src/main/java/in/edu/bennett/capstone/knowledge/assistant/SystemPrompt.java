package in.edu.bennett.capstone.knowledge.assistant;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * The system prompt of specification 11.3, a versioned resource file. The file name is the version,
 * stored with every answer that used it; a changed prompt is a new file and a new {@link #VERSION},
 * so stored answers keep naming the prompt they came from, and the older files stay. It holds no
 * secret and no internal detail, so revealing it would be harmless.
 */
@Component
class SystemPrompt {

  /**
   * Version 2 added the conversation's history (Phase 6); version 3 asks for a one-line answer
   * first, lists only for several items, and one marker per paragraph or list (ADR-039).
   */
  static final String VERSION = "answer-v3";

  private final String text;

  SystemPrompt() {
    this.text = load(VERSION);
  }

  String text() {
    return text;
  }

  String version() {
    return VERSION;
  }

  /** The prompt file {@code prompts/<version>.txt}. */
  static String load(String version) {
    ClassPathResource resource = new ClassPathResource("prompts/" + version + ".txt");
    try {
      return resource.getContentAsString(StandardCharsets.UTF_8).strip();
    } catch (IOException e) {
      throw new UncheckedIOException("The prompt " + version + " cannot be read", e);
    }
  }
}
