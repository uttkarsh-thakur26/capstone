package in.edu.bennett.capstone.knowledge.assistant;

import in.edu.bennett.capstone.knowledge.ai.ChatFailedException;
import in.edu.bennett.capstone.knowledge.ai.ChatGenerator;
import in.edu.bennett.capstone.knowledge.ai.ChatResult;
import in.edu.bennett.capstone.knowledge.assistant.Answer.Usage;
import in.edu.bennett.capstone.knowledge.retrieval.DocumentChunk;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import tools.jackson.databind.json.JsonMapper;

/**
 * Summarizes one document from its chunks (SUM-2, SUM-3, ADR-040). With no transaction open: the
 * caller reads the chunks first and stores the result afterwards (4.5).
 *
 * <ol>
 *   <li>The chunks are numbered 1 to N in the order of the text; those numbers are the {@code [n]}
 *       markers of every item.
 *   <li>Text of at most {@code single-pass-tokens} is summarized in one call. Longer text is split
 *       into groups of consecutive chunks of at most {@code map-group-tokens}; each group is
 *       summarized on its own (map), then one more call combines the partial summaries, whose items
 *       keep their markers (reduce).
 *   <li>Every item of every call is checked in code: its markers must name passages that were sent
 *       (in the reduce, passages that a partial summary cited), at least one must be left, and
 *       every number in it must appear in the passages it cites. An item that fails is dropped.
 *   <li>Pages and snippets come from the stored chunks, never from the model (RAG-3). The markers
 *       are renumbered 1, 2, 3 in the order a reader meets them.
 * </ol>
 */
class Summarizer {

  /** The prompts' version, stored with every summary (SUM-4). */
  static final String PROMPT_VERSION = "summary-v1";

  static final int MAX_KEY_POINTS = 10;
  static final int MAX_RULES = 12;
  static final int MAX_DATES = 12;

  /** How the summary was made. */
  enum Path {
    SINGLE_PASS,
    MAP_REDUCE
  }

  /**
   * The calls a document needs, known before any is made.
   *
   * @param groups the chunks of each map call; one group for a single pass
   */
  record Plan(Path path, List<List<Numbered>> groups, int tokens) {

    int modelCalls() {
      return path == Path.SINGLE_PASS ? 1 : groups.size() + 1;
    }
  }

  /** A chunk with its number in the document, 1 to N. */
  record Numbered(int n, DocumentChunk chunk) {}

  /**
   * A cited passage of the finished summary.
   *
   * @param ordinal the number its markers use in the stored summary
   */
  record Source(int ordinal, int chunkIndex, int pageStart, int pageEnd, String snippet) {}

  /** The finished summary, as it is stored. */
  record Summary(
      String overview,
      List<String> keyPoints,
      List<String> rules,
      List<String> dates,
      List<Source> sources) {}

  /** What the run produced and cost. */
  record Result(
      Summary summary,
      Plan plan,
      int modelCalls,
      String model,
      Usage usage,
      int itemsKept,
      int itemsDropped) {}

  /** The summary could not be made; what was spent until then comes with it. */
  static class SummaryFailedException extends RuntimeException {

    private final transient Progress progress;

    SummaryFailedException(String message, Progress progress, Throwable cause) {
      super(message, cause);
      this.progress = progress;
    }

    Progress progress() {
      return progress;
    }
  }

  /** The calls made so far and their tokens. */
  record Progress(int modelCalls, String model, Usage usage) {

    static Progress none() {
      return new Progress(0, null, null);
    }

    Progress after(ChatResult result) {
      return new Progress(modelCalls + 1, result.model(), ModelPrices.add(usage, Usage.of(result)));
    }
  }

  private final ChatGenerator generator;
  private final JsonMapper json;
  private final SummaryProperties properties;
  private final String mapPrompt;
  private final String reducePrompt;
  private final int snippetLength;

  Summarizer(
      ChatGenerator generator, JsonMapper json, SummaryProperties properties, int snippetLength) {
    this.generator = generator;
    this.json = json;
    this.properties = properties;
    this.mapPrompt = SystemPrompt.load("summary-map-v1");
    this.reducePrompt = SystemPrompt.load("summary-reduce-v1");
    this.snippetLength = snippetLength;
  }

  /** SUM-3: one pass, or the groups of the map step, from the chunks' own token counts. */
  Plan plan(List<DocumentChunk> chunks) {
    List<Numbered> numbered = new ArrayList<>();
    int tokens = 0;
    for (DocumentChunk chunk : chunks) {
      numbered.add(new Numbered(numbered.size() + 1, chunk));
      tokens += chunk.tokenCount();
    }
    if (tokens <= properties.singlePassTokens()) {
      return new Plan(Path.SINGLE_PASS, List.of(List.copyOf(numbered)), tokens);
    }
    List<List<Numbered>> groups = new ArrayList<>();
    List<Numbered> group = new ArrayList<>();
    int used = 0;
    for (Numbered chunk : numbered) {
      int size = chunk.chunk().tokenCount();
      if (!group.isEmpty() && used + size > properties.mapGroupTokens()) {
        groups.add(List.copyOf(group));
        group = new ArrayList<>();
        used = 0;
      }
      group.add(chunk);
      used += size;
    }
    groups.add(List.copyOf(group));
    return new Plan(Path.MAP_REDUCE, List.copyOf(groups), tokens);
  }

  /**
   * @param plan from {@link #plan}, already checked against {@code max-model-calls}
   * @throws SummaryFailedException when a call fails, its output is malformed, or no item is left
   */
  Result summarize(String title, Plan plan) {
    Map<Integer, Numbered> byNumber = new HashMap<>();
    plan.groups().forEach(group -> group.forEach(chunk -> byNumber.put(chunk.n(), chunk)));
    Progress progress = Progress.none();
    // Items the checks removed, in every call, and items beyond the lists' limits: what the model
    // wrote and the reader never sees. Items the reduce merged are not counted.
    int dropped = 0;
    SummaryOutput combined;
    if (plan.path() == Path.SINGLE_PASS) {
      Call call = call(mapPrompt, passages(title, plan.groups().getFirst(), 0, 1), progress);
      progress = call.progress();
      combined = checked(call.output(), allowed(plan.groups().getFirst()), byNumber);
      dropped += call.output().items().size() - combined.items().size();
    } else {
      List<SummaryOutput> partials = new ArrayList<>();
      int total = plan.groups().size();
      for (int i = 0; i < total; i++) {
        List<Numbered> group = plan.groups().get(i);
        Call call = call(mapPrompt, passages(title, group, i + 1, total), progress);
        progress = call.progress();
        SummaryOutput partial = checked(call.output(), allowed(group), byNumber);
        dropped += call.output().items().size() - partial.items().size();
        partials.add(partial);
      }
      Set<Integer> cited = new TreeSet<>();
      partials.forEach(
          partial -> partial.items().forEach(item -> cited.addAll(Grounding.markers(item))));
      if (cited.isEmpty()) {
        // Nothing survived the map step, so a reduce call could only cost money.
        throw new SummaryFailedException(
            "The model's summary had no item tied to the document's text.", progress, null);
      }
      Call call = call(reducePrompt, partials(title, plan.groups(), partials), progress);
      progress = call.progress();
      combined = checked(call.output(), cited, byNumber);
      dropped += call.output().items().size() - combined.items().size();
    }
    int beforeLimits = combined.items().size();
    combined = combined.capped(MAX_KEY_POINTS, MAX_RULES, MAX_DATES);
    int kept = combined.items().size();
    dropped += beforeLimits - kept;
    if (kept == 0) {
      throw new SummaryFailedException(
          "The model's summary had no item tied to the document's text.", progress, null);
    }
    Summary summary = finish(combined, byNumber);
    return new Result(
        summary, plan, progress.modelCalls(), progress.model(), progress.usage(), kept, dropped);
  }

  private record Call(SummaryOutput output, Progress progress) {}

  private Call call(String systemPrompt, String userMessage, Progress progress) {
    ChatResult result;
    try {
      result = generator.generate(systemPrompt, userMessage, SummaryOutput.SCHEMA);
    } catch (ChatFailedException e) {
      throw new SummaryFailedException("The chat model gave no answer.", progress, e);
    }
    Optional<SummaryOutput> output = SummaryOutput.parse(json, result.text());
    Progress after = progress.after(result);
    if (output.isEmpty()) {
      throw new SummaryFailedException("The chat model's output was malformed.", after, null);
    }
    return new Call(output.get(), after);
  }

  /**
   * Every item keeps only the markers in {@code allowed}; an item left with none, with no text, or
   * with a number that none of its cited passages holds, is dropped.
   */
  private static SummaryOutput checked(
      SummaryOutput output, Set<Integer> allowed, Map<Integer, Numbered> byNumber) {
    return output.map(
        item -> {
          String kept = Grounding.keepMarkers(item, allowed::contains);
          List<Integer> cited = Grounding.markers(kept);
          if (cited.isEmpty() || !Grounding.hasText(kept)) {
            return "";
          }
          List<String> texts = cited.stream().map(n -> byNumber.get(n).chunk().text()).toList();
          return Grounding.numbersAppearIn(kept, texts) ? kept : "";
        });
  }

  private static Set<Integer> allowed(List<Numbered> group) {
    Set<Integer> numbers = new TreeSet<>();
    group.forEach(chunk -> numbers.add(chunk.n()));
    return numbers;
  }

  /** The markers renumbered 1, 2, 3 in reading order, with the sources they now name. */
  private Summary finish(SummaryOutput output, Map<Integer, Numbered> byNumber) {
    Map<Integer, Integer> numbers = new LinkedHashMap<>();
    for (String item : output.items()) {
      for (int n : Grounding.markers(item)) {
        numbers.putIfAbsent(n, numbers.size() + 1);
      }
    }
    SummaryOutput renumbered = output.map(item -> Grounding.renumber(item, numbers));
    List<Source> sources = new ArrayList<>();
    numbers.forEach(
        (n, ordinal) -> {
          DocumentChunk chunk = byNumber.get(n).chunk();
          sources.add(
              new Source(
                  ordinal,
                  chunk.chunkIndex(),
                  chunk.pageStart(),
                  chunk.pageEnd(),
                  AnswerService.snippet(chunk.text(), snippetLength)));
        });
    return new Summary(
        renumbered.overview().isBlank() ? null : renumbered.overview(),
        renumbered.keyPoints(),
        renumbered.rules(),
        renumbered.dates(),
        List.copyOf(sources));
  }

  /** The user message of a map call or a single pass: the title, then the numbered passages. */
  private static String passages(String title, List<Numbered> group, int part, int parts) {
    StringBuilder message =
        new StringBuilder("<document title=\"")
            .append(PromptAssembler.escape(title))
            .append("\">\n");
    message.append(
        part == 0 ? "<passages>\n" : "<passages part=\"%d of %d\">\n".formatted(part, parts));
    for (Numbered chunk : group) {
      message
          .append("<passage n=\"")
          .append(chunk.n())
          .append("\" pages=\"")
          .append(pages(chunk.chunk()))
          .append("\">\n")
          .append(PromptAssembler.escape(chunk.chunk().text()))
          .append("\n</passage>\n");
    }
    message.append("</passages>\n</document>\n\n");
    message.append(
        part == 0
            ? "Summarize this document."
            : "Summarize these passages, part %d of %d of the document.".formatted(part, parts));
    return message.toString();
  }

  /** The user message of the reduce call: the partial summaries, with their markers. */
  private static String partials(
      String title, List<List<Numbered>> groups, List<SummaryOutput> partials) {
    StringBuilder message =
        new StringBuilder("<document title=\"")
            .append(PromptAssembler.escape(title))
            .append("\">\n");
    message.append("<partials>\n");
    for (int i = 0; i < partials.size(); i++) {
      List<Numbered> group = groups.get(i);
      SummaryOutput partial = partials.get(i);
      message
          .append("<partial n=\"")
          .append(i + 1)
          .append("\" passages=\"")
          .append(group.getFirst().n())
          .append('-')
          .append(group.getLast().n())
          .append("\">\n");
      if (!partial.overview().isBlank()) {
        message
            .append("Overview: ")
            .append(PromptAssembler.escape(partial.overview()))
            .append('\n');
      }
      list(message, "Key points", partial.keyPoints());
      list(message, "Rules", partial.rules());
      list(message, "Dates", partial.dates());
      message.append("</partial>\n");
    }
    message.append("</partials>\n</document>\n\n");
    message.append("Combine these partial summaries into one summary of the whole document.");
    return message.toString();
  }

  private static void list(StringBuilder message, String heading, List<String> items) {
    if (items.isEmpty()) {
      return;
    }
    message.append(heading).append(":\n");
    items.forEach(item -> message.append("- ").append(PromptAssembler.escape(item)).append('\n'));
  }

  private static String pages(DocumentChunk chunk) {
    return chunk.pageStart() == chunk.pageEnd()
        ? String.valueOf(chunk.pageStart())
        : chunk.pageStart() + "-" + chunk.pageEnd();
  }
}
