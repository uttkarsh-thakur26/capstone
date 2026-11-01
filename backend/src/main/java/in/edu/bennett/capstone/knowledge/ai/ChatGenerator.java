package in.edu.bennett.capstone.knowledge.ai;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.StructuredOutputChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Sends one request to the chat model and asks for output that follows a JSON schema (11.1 step 7).
 * It holds no provider type: the model and its settings come from Spring AI's configuration ({@code
 * spring.ai.model.chat} and the provider's own properties), so another provider is a starter and
 * its properties, not code (3.4, D5).
 *
 * <p>The model is called directly, not through {@code ChatClient}, so no advisor can add tools
 * (RAG-7) or call the model again to check the schema (S16). Retries and the timeout belong to the
 * provider's client (RAG-6).
 */
@Component
public class ChatGenerator {

  private static final Logger log = LoggerFactory.getLogger(ChatGenerator.class);

  private final ObjectProvider<ChatModel> models;

  ChatGenerator(ObjectProvider<ChatModel> models) {
    this.models = models;
  }

  /**
   * One request: the system prompt, one user message, and the schema the output must follow.
   *
   * @throws ChatFailedException when no model is configured or the model gives no answer
   */
  public ChatResult generate(String systemPrompt, String userMessage, String outputSchema) {
    ChatModel model = models.getIfUnique();
    if (model == null) {
      throw new ChatFailedException("No chat model is configured (spring.ai.model.chat)");
    }
    ChatOptions options = withSchema(model.getOptions(), outputSchema);
    Prompt prompt =
        new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(userMessage)), options);
    ChatResponse response;
    try {
      response = model.call(prompt);
    } catch (RuntimeException e) {
      // The exception describes the failure, never the prompt (SEC-10).
      log.warn("The chat model failed: {}", e.toString());
      throw new ChatFailedException("The chat model failed", e);
    }
    return result(response, options);
  }

  /**
   * The model's own default options with the schema added. A request's options replace the model's
   * defaults instead of merging with them, so they are built from those defaults.
   */
  private static ChatOptions withSchema(ChatOptions defaults, String outputSchema) {
    if (!(defaults instanceof StructuredOutputChatOptions structured)) {
      throw new ChatFailedException(
          "The configured chat model cannot be asked for structured output (RAG-9)");
    }
    return structured.mutate().outputSchema(outputSchema).build();
  }

  private static ChatResult result(ChatResponse response, ChatOptions options) {
    Generation generation = response == null ? null : response.getResult();
    String text = generation == null ? null : generation.getOutput().getText();
    ChatResponseMetadata metadata = response == null ? null : response.getMetadata();
    String model = metadata == null ? null : metadata.getModel();
    if (model == null || model.isBlank()) {
      model = options.getModel();
    }
    Usage usage = metadata == null ? null : metadata.getUsage();
    return new ChatResult(
        text == null ? "" : text,
        model,
        usage == null ? null : usage.getPromptTokens(),
        usage == null ? null : usage.getCompletionTokens(),
        usage == null ? null : usage.getCacheReadInputTokens());
  }
}
