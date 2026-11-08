package in.edu.bennett.capstone.knowledge;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.StructuredOutputChatOptions;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The scripted chat model of specification 19.1. Tests choose what the next calls return: a valid
 * answer, {@code answerable=false}, a citation number that does not exist, malformed output, or a
 * failure such as a timeout. With nothing scripted it answers from source 1. It records every
 * request it receives, so tests can read exactly what the model was shown, and whether a
 * transaction was open during the call (4.5).
 *
 * <p>One instance serves every test of a Spring context: tests call {@link #reset()} afterwards.
 */
public class FakeChatModel implements ChatModel {

  public static final String MODEL = "fake-chat";

  /** The output when a test scripts nothing: an answer that cites source 1. */
  public static final String DEFAULT_ANSWER =
      """
      {"answerable": true, "answer": "The sources say so [1].", "citations": [1]}""";

  /** One request as the model received it. */
  public record Request(
      String system, String user, String outputSchema, Double temperature, List<String> types) {}

  private final Deque<Supplier<String>> script = new ArrayDeque<>();
  private final List<Request> requests = new CopyOnWriteArrayList<>();
  private final AtomicInteger callsInTransaction = new AtomicInteger();

  @Override
  public ChatOptions getOptions() {
    return StructuredOutputChatOptions.builder().model(MODEL).build();
  }

  @Override
  public ChatResponse call(Prompt prompt) {
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      callsInTransaction.incrementAndGet();
    }
    Request request = request(prompt);
    requests.add(request);
    Supplier<String> next;
    synchronized (script) {
      next = script.poll();
    }
    String output = next == null ? defaultFor(request) : next.get();
    return new ChatResponse(
        List.of(new Generation(new AssistantMessage(output))),
        ChatResponseMetadata.builder().model(MODEL).usage(new DefaultUsage(1200, 80)).build());
  }

  /** The next call returns this output. */
  public void answerNext(String output) {
    synchronized (script) {
      script.add(() -> output);
    }
  }

  /** The next call throws this. */
  public void failNext(Supplier<RuntimeException> failure) {
    synchronized (script) {
      script.add(
          () -> {
            throw failure.get();
          });
    }
  }

  /**
   * The next call waits until {@code release} opens, then returns the output, or throws the
   * exception, that {@code outcome} gives.
   *
   * @return a latch that opens when that call has started
   */
  public CountDownLatch holdNext(CountDownLatch release, Supplier<String> outcome) {
    CountDownLatch started = new CountDownLatch(1);
    synchronized (script) {
      script.add(
          () -> {
            started.countDown();
            try {
              if (!release.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("A held chat call was never released");
              }
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              throw new IllegalStateException(e);
            }
            return outcome.get();
          });
    }
    return started;
  }

  public List<Request> requests() {
    return requests;
  }

  public int calls() {
    return requests.size();
  }

  public int callsInTransaction() {
    return callsInTransaction.get();
  }

  public void reset() {
    synchronized (script) {
      script.clear();
    }
    requests.clear();
    callsInTransaction.set(0);
  }

  /**
   * With nothing scripted, an output that fits the schema asked for: an answer from source 1, a
   * summary citing the first passage or marker the prompt holds, or one difference quoting the
   * first words of the first candidate's passages. Those defaults let the end-to-end test run each
   * feature without scripting.
   */
  private static String defaultFor(Request request) {
    String schema = request.outputSchema() == null ? "" : request.outputSchema();
    String user = request.user() == null ? "" : request.user();
    if (schema.contains("\"keyPoints\"")) {
      Matcher number = SUMMARY_SOURCE.matcher(user);
      String n =
          number.find() ? (number.group(1) != null ? number.group(1) : number.group(2)) : "1";
      return """
          {"overview": "The document sets out its rules [%1$s].",
           "keyPoints": ["It states what readers must do [%1$s]."], "rules": [], "dates": []}"""
          .formatted(n);
    }
    if (schema.contains("\"differences\"")) {
      String left = firstWords(CANDIDATE_LEFT.matcher(user));
      String right = firstWords(CANDIDATE_RIGHT.matcher(user));
      if (left == null && right == null) {
        return "{\"differences\": []}";
      }
      String category = left == null ? "ADDED" : right == null ? "REMOVED" : "OTHER";
      return """
          {"differences": [{"candidate": 1, "category": "%s", "leftQuote": "%s",
            "rightQuote": "%s", "explanation": "The two passages say different things."}]}"""
          .formatted(category, left == null ? "" : left, right == null ? "" : right);
    }
    return DEFAULT_ANSWER;
  }

  /** The first passage number of a map call, or the first marker of a reduce call. */
  private static final Pattern SUMMARY_SOURCE = Pattern.compile("<passage n=\"(\\d+)\"|\\[(\\d+)]");

  private static final Pattern CANDIDATE_LEFT =
      Pattern.compile("<candidate n=\"1\">\n(?:<left pages=\"[^\"]*\">\n([^<]*)\n</left>)?");

  private static final Pattern CANDIDATE_RIGHT =
      Pattern.compile(
          "<candidate n=\"1\">\n(?:<left[^>]*>\n[^<]*\n</left>\n)?<right pages=\"[^\"]*\">\n([^<]*)\n</right>");

  /** The first four words of the matched passage, unescaped, or null when there is none. */
  private static String firstWords(Matcher passage) {
    if (!passage.find() || passage.group(1) == null) {
      return null;
    }
    String text =
        passage
            .group(1)
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&amp;", "&");
    String[] words = text.strip().split("\\s+");
    return String.join(" ", java.util.Arrays.copyOf(words, Math.min(4, words.length)))
        .replace("\\", "\\\\")
        .replace("\"", "\\\"");
  }

  private static Request request(Prompt prompt) {
    List<Message> messages = prompt.getInstructions();
    String system = null;
    String user = null;
    for (Message message : messages) {
      if (message.getMessageType() == MessageType.SYSTEM) {
        system = message.getText();
      } else if (message.getMessageType() == MessageType.USER) {
        user = message.getText();
      }
    }
    String schema =
        prompt.getOptions() instanceof StructuredOutputChatOptions structured
            ? structured.getOutputSchema()
            : null;
    return new Request(
        system,
        user,
        schema,
        prompt.getOptions() == null ? null : prompt.getOptions().getTemperature(),
        messages.stream().map(message -> message.getMessageType().name()).toList());
  }
}
