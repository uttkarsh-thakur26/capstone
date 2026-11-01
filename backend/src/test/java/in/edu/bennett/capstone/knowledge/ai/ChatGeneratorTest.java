package in.edu.bennett.capstone.knowledge.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import in.edu.bennett.capstone.knowledge.FakeChatModel;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.StructuredOutputChatOptions;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** The provider-neutral chat call (D5, RAG-6, RAG-7) and the startup check of the OpenAI key. */
class ChatGeneratorTest {

  private static final String SCHEMA = "{\"type\": \"object\"}";

  @Test
  void theRequestKeepsTheModelsDefaultsAndAddsTheSchema() {
    CapturingModel model =
        new CapturingModel(
            StructuredOutputChatOptions.builder()
                .model("m")
                .temperature(0.1)
                .maxTokens(500)
                .build());

    ChatResult result = generator(model).generate("system text", "user text", SCHEMA);

    StructuredOutputChatOptions sent = (StructuredOutputChatOptions) model.prompt.getOptions();
    assertThat(sent.getOutputSchema()).isEqualTo(SCHEMA);
    assertThat(sent.getModel()).isEqualTo("m");
    assertThat(sent.getTemperature()).isEqualTo(0.1);
    assertThat(sent.getMaxTokens()).isEqualTo(500);
    assertThat(model.prompt.getInstructions())
        .extracting(message -> message.getMessageType().name(), message -> message.getText())
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("SYSTEM", "system text"),
            org.assertj.core.groups.Tuple.tuple("USER", "user text"));
    assertThat(result).isEqualTo(new ChatResult("{}", "m-2026", 1200, 80, null));
  }

  @Test
  void theConfiguredNameIsUsedWhenTheResponseNamesNoModel() {
    CapturingModel model =
        new CapturingModel(StructuredOutputChatOptions.builder().model("m").build());
    model.responseModel = null;

    assertThat(generator(model).generate("s", "u", SCHEMA).model()).isEqualTo("m");
  }

  @Test
  void aFailureOfTheModelIsAChatFailure() {
    FakeChatModel model = new FakeChatModel();
    model.failNext(() -> new IllegalStateException("timeout"));

    assertThatThrownBy(() -> generator(model).generate("s", "u", SCHEMA))
        .isInstanceOf(ChatFailedException.class)
        .hasRootCauseMessage("timeout");
  }

  @Test
  void noConfiguredModelIsAChatFailure() {
    ChatGenerator generator =
        new ChatGenerator(new StaticListableBeanFactory().getBeanProvider(ChatModel.class));

    assertThatThrownBy(() -> generator.generate("s", "u", SCHEMA))
        .isInstanceOf(ChatFailedException.class);
  }

  @Test
  void aModelWithoutStructuredOutputIsAChatFailure() {
    CapturingModel model = new CapturingModel(ChatOptions.builder().model("m").build());

    assertThatThrownBy(() -> generator(model).generate("s", "u", SCHEMA))
        .isInstanceOf(ChatFailedException.class)
        .hasMessageContaining("RAG-9");
    assertThat(model.prompt).isNull();
  }

  @Test
  void anOpenAiProviderWithoutAKeyStopsStartup() {
    ApplicationContextRunner runner =
        new ApplicationContextRunner()
            .withUserConfiguration(AiConfig.class)
            .withPropertyValues(
                "spring.ai.model.embedding=none",
                "app.embedding.model=m",
                "app.embedding.dimensions=8");

    runner
        .withPropertyValues("spring.ai.model.chat=openai", "spring.ai.openai.api-key= ")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("OPENAI_API_KEY is not set"));
    runner
        .withPropertyValues("spring.ai.model.chat=openai", "spring.ai.openai.api-key=sk-test")
        .run(context -> assertThat(context).hasNotFailed());
    runner
        .withPropertyValues("spring.ai.model.chat=none")
        .run(context -> assertThat(context).hasNotFailed());
  }

  private static ChatGenerator generator(ChatModel model) {
    return new ChatGenerator(
        new StaticListableBeanFactory(Map.of("model", model)).getBeanProvider(ChatModel.class));
  }

  /** Returns "{}" and keeps the prompt it was sent. */
  private static final class CapturingModel implements ChatModel {

    private final ChatOptions options;
    private Prompt prompt;
    private String responseModel = "m-2026";

    CapturingModel(ChatOptions options) {
      this.options = options;
    }

    @Override
    public ChatOptions getOptions() {
      return options;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
      this.prompt = prompt;
      ChatResponseMetadata.Builder metadata =
          ChatResponseMetadata.builder().usage(new DefaultUsage(1200, 80));
      if (responseModel != null) {
        metadata.model(responseModel);
      }
      return new ChatResponse(
          List.of(new Generation(new AssistantMessage("{}"))), metadata.build());
    }
  }
}
