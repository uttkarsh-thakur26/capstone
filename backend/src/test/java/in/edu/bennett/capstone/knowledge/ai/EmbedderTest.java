package in.edu.bennett.capstone.knowledge.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import in.edu.bennett.capstone.knowledge.FakeEmbeddingModel;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/** ING-8, ING-9 and the request Ollama receives (ADR-033, ADR-034). */
class EmbedderTest {

  private static final int DIMENSIONS = 8;

  private final FakeEmbeddingModel model = new FakeEmbeddingModel(DIMENSIONS);
  private final EmbeddingProperties properties = properties(DIMENSIONS);
  private final Embedder embedder =
      new Embedder(model, EmbeddingOptions.builder().model("m").build(), properties);

  static EmbeddingProperties properties(int dimensions) {
    return new EmbeddingProperties(
        "qwen3-embedding:0.6b",
        dimensions,
        2048,
        1.5,
        "Instruct: Find passages\nQuery:",
        new EmbeddingProperties.Retry(3, Duration.ofMillis(1), 2.0, Duration.ofMillis(5)),
        false);
  }

  @Test
  void documentsAreEmbeddedAsTheyAreAndQueriesWithThePrefix() {
    embedder.embedDocuments(List.of("Fee rules"));
    embedder.embedQuery("What is the fee?");

    assertThat(model.requests())
        .containsExactly(
            List.of("Fee rules"), List.of("Instruct: Find passages\nQuery:What is the fee?"));
  }

  @Test
  void transientFailuresAreRetriedWithBackoff() {
    model.failNext(() -> new TransientAiException("503"), 2);
    model.failNext(() -> new ResourceAccessException("read timed out"), 1);

    assertThat(embedder.embedDocuments(List.of("a", "b"))).hasSize(2);
    assertThat(model.requests()).hasSize(4);
  }

  @Test
  void aClientErrorIsNotRetried() {
    model.failNext(() -> new NonTransientAiException("400 input too long"), 1);

    assertThatThrownBy(() -> embedder.embedDocuments(List.of("a")))
        .isInstanceOf(EmbeddingFailedException.class)
        .hasCauseInstanceOf(NonTransientAiException.class);
    assertThat(model.requests()).hasSize(1);
  }

  @Test
  void aVectorOfTheWrongLengthIsRefused() {
    model.answerWithDimensions(4);

    assertThatThrownBy(() -> embedder.embedDocuments(List.of("a")))
        .isInstanceOf(EmbeddingFailedException.class)
        .hasMessageContaining("8 dimensions");
  }

  @Test
  void anEmptyTextIsNeverSent() {
    assertThatIllegalArgumentException().isThrownBy(() -> embedder.embedDocuments(List.of(" ")));
    assertThatIllegalArgumentException().isThrownBy(() -> embedder.embedQuery(""));
    assertThat(model.requests()).isEmpty();
  }

  /** 19.2: a dimension mismatch stops startup (ING-9). */
  @Test
  void aDimensionMismatchStopsStartup() {
    model.answerWithDimensions(384);

    assertThatIllegalStateException()
        .isThrownBy(() -> new EmbeddingModelCheck(embedder).afterSingletonsInstantiated())
        .withMessageContaining("384")
        .withMessageContaining("ING-9");
  }

  /** ADR-034: a model that does not answer does not stop startup; the worker waits for it. */
  @Test
  void anUnreachableModelDoesNotStopStartupAndIsCheckedAgainLater() {
    EmbeddingModelCheck check = new EmbeddingModelCheck(embedder);
    model.failNext(() -> new ResourceAccessException("Connection refused"), 1);

    check.afterSingletonsInstantiated();
    assertThat(check.ensureVerified()).isTrue();
  }

  /** After startup, a mismatch pauses the worker instead of throwing. */
  @Test
  void aMismatchFoundAfterStartupPausesInsteadOfThrowing() {
    EmbeddingModelCheck check = new EmbeddingModelCheck(embedder);
    model.failNext(() -> new ResourceAccessException("Connection refused"), 1);
    check.afterSingletonsInstantiated();
    model.answerWithDimensions(384);

    assertThat(check.ensureVerified()).isFalse();
    assertThat(check.ensureVerified()).isFalse();
  }

  /**
   * The blocking review finding: Spring AI's own {@code embed(...)} shortcuts drop {@code truncate}
   * and {@code num_ctx}, so Ollama would cut long input silently. The request built from {@link
   * AiConfig} must carry both.
   */
  @Test
  void theRequestToOllamaTurnsTruncationOffAndSetsTheContext() {
    RestClient.Builder rest = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
    server
        .expect(requestTo("http://ollama.test/api/embed"))
        .andExpect(jsonPath("$.model").value("qwen3-embedding:0.6b"))
        .andExpect(jsonPath("$.truncate").value(false))
        .andExpect(jsonPath("$.options.num_ctx").value(2048))
        .andExpect(jsonPath("$.input[0]").value("A chunk"))
        .andRespond(
            withSuccess(
                "{\"model\":\"qwen3-embedding:0.6b\",\"embeddings\":[[0.6,0.8]],\"prompt_eval_count\":3}",
                MediaType.APPLICATION_JSON));
    OllamaApi api =
        OllamaApi.builder().baseUrl("http://ollama.test").restClientBuilder(rest).build();
    AiConfig config = new AiConfig();
    EmbeddingProperties two = properties(2);
    EmbeddingOptions options = config.embeddingRequestOptions(two);
    OllamaEmbeddingModel ollama =
        config.ollamaEmbeddingModel(
            api,
            options,
            new StaticListableBeanFactory()
                .getBeanProvider(io.micrometer.observation.ObservationRegistry.class));

    List<float[]> vectors = new Embedder(ollama, options, two).embedDocuments(List.of("A chunk"));

    server.verify();
    assertThat(vectors).singleElement().satisfies(v -> assertThat(v).containsExactly(0.6f, 0.8f));
  }
}
