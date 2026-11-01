package in.edu.bennett.capstone.knowledge.assistant;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * The system prompt of specification 11.3, a versioned resource file. The file name is the version,
 * stored with every answer that used it; a changed prompt is a new file and a new {@link #VERSION},
 * so stored answers keep naming the prompt they came from. It holds no secret and no internal
 * detail, so revealing it would be harmless.
 */
@Component
class SystemPrompt {

  static final String VERSION = "answer-v1";

  private final String text;

  SystemPrompt() {
    ClassPathResource resource = new ClassPathResource("prompts/" + VERSION + ".txt");
    try {
      this.text = resource.getContentAsString(StandardCharsets.UTF_8).strip();
    } catch (IOException e) {
      throw new UncheckedIOException("The system prompt " + VERSION + " cannot be read", e);
    }
  }

  String text() {
    return text;
  }

  String version() {
    return VERSION;
  }
}
