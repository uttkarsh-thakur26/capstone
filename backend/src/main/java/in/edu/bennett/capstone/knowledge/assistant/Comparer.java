package in.edu.bennett.capstone.knowledge.assistant;

import in.edu.bennett.capstone.knowledge.ai.ChatFailedException;
import in.edu.bennett.capstone.knowledge.ai.ChatGenerator;
import in.edu.bennett.capstone.knowledge.ai.ChatResult;
import in.edu.bennett.capstone.knowledge.assistant.Answer.Usage;
import in.edu.bennett.capstone.knowledge.assistant.ComparisonResponse.Category;
import in.edu.bennett.capstone.knowledge.retrieval.ChunkSimilarity;
import in.edu.bennett.capstone.knowledge.retrieval.DocumentChunk;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Compares two documents from their chunks: diff first, explain second (CMP-2, ADR-040). With no
 * transaction open: the caller reads the chunks and their similarities first (4.5).
 *
 * <ol>
 *   <li>Align: a left and a right chunk are paired when each is the other's closest by embedding
 *       similarity, above a low {@code align-floor}. This finds the same passage in two versions,
 *       and the matching part of two documents that are organized differently. A chunk with no
 *       partner is on one side only.
 *   <li>Diff: a pair whose two texts read the same is dropped and never sent.
 *   <li>Explain: one model call classifies the differences of the remaining candidates, quoting
 *       each side.
 *   <li>Check, in code: the quote must be in that candidate's passage; an added text must be found
 *       nowhere in the left document and a removed one nowhere in the right; a changed text must
 *       differ, and be missing from the other document on at least one side; every number in the
 *       explanation must be in the quotes. A difference that fails is discarded (CMP-4). The quote
 *       shown is the stored text, and its pages come from the stored chunk.
 * </ol>
 */
class Comparer {

  static final String PROMPT_VERSION = "compare-v1";

  /** A pair of passages, or one passage with {@code null} on the other side. */
  record Candidate(int n, DocumentChunk left, DocumentChunk right) {}

  /** The alignment: what goes to the model, and how much read the same. */
  record Alignment(List<Candidate> candidates, int identical) {}

  /** A difference that passed every check. */
  record Checked(
      Category category,
      String explanation,
      String leftQuote,
      DocumentChunk left,
      String rightQuote,
      DocumentChunk right) {}

  /** What the comparison found and cost. */
  record Result(
      Alignment alignment,
      List<Checked> differences,
      int dropped,
      int modelCalls,
      String model,
      Usage usage) {}

  /** The comparison could not be made; what was spent comes with it. */
  static class ComparisonFailedException extends RuntimeException {

    private final transient Usage usage;
    private final String model;

    ComparisonFailedException(String message, String model, Usage usage, Throwable cause) {
      super(message, cause);
      this.model = model;
      this.usage = usage;
    }

    Usage usage() {
      return usage;
    }

    String model() {
      return model;
    }
  }

  /** The schema of the one call, strict: every property required, no other. */
  static final String SCHEMA =
      """
      {
        "type": "object",
        "properties": {
          "differences": {
            "type": "array",
            "items": {
              "type": "object",
              "properties": {
                "candidate": {"type": "integer"},
                "category": {"type": "string", "enum": ["ADDED", "REMOVED", "CHANGED_REQUIREMENT",
                    "CHANGED_DATE", "CHANGED_THRESHOLD", "OTHER"]},
                "leftQuote": {"type": "string"},
                "rightQuote": {"type": "string"},
                "explanation": {"type": "string"}
              },
              "required": ["candidate", "category", "leftQuote", "rightQuote", "explanation"],
              "additionalProperties": false
            }
          }
        },
        "required": ["differences"],
        "additionalProperties": false
      }""";

  private static final Set<String> FIELDS =
      Set.of("candidate", "category", "leftQuote", "rightQuote", "explanation");

  private final ChatGenerator generator;
  private final JsonMapper json;
  private final ComparisonProperties properties;
  private final String prompt;

  Comparer(ChatGenerator generator, JsonMapper json, ComparisonProperties properties) {
    this.generator = generator;
    this.json = json;
    this.properties = properties;
    this.prompt = SystemPrompt.load(PROMPT_VERSION);
  }

  /**
   * CMP-2: a left and a right chunk are paired when each is the other's closest, and closer than
   * {@code align-floor}; identical pairs are counted. Mutual closeness does not depend on how
   * similar the two documents are overall, so it pairs the same passage in two versions (similarity
   * near 1) and the matching sections of two documents built on one template (about 0.25 to 0.3
   * measured on two course documents), where any fixed floor high enough to mean "same passage"
   * would pair nothing (ADR-040).
   */
  Alignment align(
      List<DocumentChunk> left, List<DocumentChunk> right, List<ChunkSimilarity> similarities) {
    Map<Integer, DocumentChunk> rightByIndex = byIndex(right);
    Comparator<ChunkSimilarity> closer =
        Comparator.comparingDouble(ChunkSimilarity::similarity)
            .thenComparing(ChunkSimilarity::leftIndex, Comparator.reverseOrder())
            .thenComparing(ChunkSimilarity::rightIndex, Comparator.reverseOrder());
    Map<Integer, ChunkSimilarity> bestForLeft = new HashMap<>();
    Map<Integer, ChunkSimilarity> bestForRight = new HashMap<>();
    for (ChunkSimilarity s : similarities) {
      if (s.similarity() < properties.alignFloor()) {
        continue;
      }
      bestForLeft.merge(s.leftIndex(), s, (a, b) -> closer.compare(a, b) >= 0 ? a : b);
      bestForRight.merge(s.rightIndex(), s, (a, b) -> closer.compare(a, b) >= 0 ? a : b);
    }
    Map<Integer, Integer> partner = new HashMap<>();
    Set<Integer> rightTaken = new HashSet<>();
    bestForLeft.forEach(
        (leftIndex, best) -> {
          ChunkSimilarity back = bestForRight.get(best.rightIndex());
          if (back != null && back.leftIndex() == leftIndex) {
            partner.put(leftIndex, best.rightIndex());
            rightTaken.add(best.rightIndex());
          }
        });
    List<Candidate> candidates = new ArrayList<>();
    int identical = 0;
    for (DocumentChunk chunk : left) {
      Integer other = partner.get(chunk.chunkIndex());
      DocumentChunk match = other == null ? null : rightByIndex.get(other);
      if (match != null
          && Grounding.comparable(chunk.text()).equals(Grounding.comparable(match.text()))) {
        identical++;
        continue;
      }
      candidates.add(new Candidate(candidates.size() + 1, chunk, match));
    }
    for (DocumentChunk chunk : right) {
      if (!rightTaken.contains(chunk.chunkIndex())) {
        candidates.add(new Candidate(candidates.size() + 1, null, chunk));
      }
    }
    return new Alignment(List.copyOf(candidates), identical);
  }

  /**
   * @throws ComparisonFailedException when the call fails or its output is malformed
   */
  Result compare(
      String leftTitle,
      String rightTitle,
      List<DocumentChunk> left,
      List<DocumentChunk> right,
      Alignment alignment) {
    if (alignment.candidates().isEmpty()) {
      return new Result(alignment, List.of(), 0, 0, null, null);
    }
    ChatResult result;
    try {
      result = generator.generate(prompt, message(leftTitle, rightTitle, alignment), SCHEMA);
    } catch (ChatFailedException e) {
      throw new ComparisonFailedException("The chat model gave no answer.", null, null, e);
    }
    Usage usage = Usage.of(result);
    Optional<List<Raw>> raw = parse(result.text());
    if (raw.isEmpty()) {
      throw new ComparisonFailedException(
          "The chat model's output was malformed.", result.model(), usage, null);
    }
    List<String> leftTexts = left.stream().map(DocumentChunk::text).toList();
    List<String> rightTexts = right.stream().map(DocumentChunk::text).toList();
    List<Checked> kept = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (Raw difference : raw.get()) {
      Optional<Checked> checked = check(difference, alignment, leftTexts, rightTexts);
      if (checked.isPresent()
          && kept.size() < properties.maxDifferences()
          && seen.add(key(checked.get()))) {
        kept.add(checked.get());
      }
    }
    return new Result(
        alignment, List.copyOf(kept), raw.get().size() - kept.size(), 1, result.model(), usage);
  }

  /** The model's difference, before any check. */
  record Raw(
      int candidate, Category category, String leftQuote, String rightQuote, String explanation) {}

  private Optional<Checked> check(
      Raw raw, Alignment alignment, List<String> leftTexts, List<String> rightTexts) {
    if (raw.candidate() < 1 || raw.candidate() > alignment.candidates().size()) {
      return Optional.empty();
    }
    Candidate candidate = alignment.candidates().get(raw.candidate() - 1);
    String explanation = raw.explanation().strip();
    if (explanation.isEmpty()) {
      return Optional.empty();
    }
    boolean added = raw.category() == Category.ADDED;
    boolean removed = raw.category() == Category.REMOVED;
    String leftQuote = added ? "" : raw.leftQuote().strip();
    String rightQuote = removed ? "" : raw.rightQuote().strip();
    if ((!added && leftQuote.isEmpty()) || (!removed && rightQuote.isEmpty())) {
      return Optional.empty();
    }
    if (leftQuote.length() > properties.maxQuoteLength()
        || rightQuote.length() > properties.maxQuoteLength()) {
      return Optional.empty();
    }
    Optional<String> leftText = Optional.empty();
    Optional<String> rightText = Optional.empty();
    if (!added) {
      if (candidate.left() == null) {
        return Optional.empty();
      }
      leftText = Grounding.locate(candidate.left().text(), leftQuote);
      if (leftText.isEmpty()) {
        return Optional.empty();
      }
    }
    if (!removed) {
      if (candidate.right() == null) {
        return Optional.empty();
      }
      rightText = Grounding.locate(candidate.right().text(), rightQuote);
      if (rightText.isEmpty()) {
        return Optional.empty();
      }
    }
    if (added && Grounding.inAny(rightQuote, leftTexts)) {
      return Optional.empty();
    }
    if (removed && Grounding.inAny(leftQuote, rightTexts)) {
      return Optional.empty();
    }
    if (!added && !removed) {
      boolean same = Grounding.comparable(leftQuote).equals(Grounding.comparable(rightQuote));
      boolean bothElsewhere =
          Grounding.inAny(leftQuote, rightTexts) && Grounding.inAny(rightQuote, leftTexts);
      if (same || bothElsewhere) {
        return Optional.empty();
      }
    }
    List<String> quotes = new ArrayList<>();
    leftText.ifPresent(quotes::add);
    rightText.ifPresent(quotes::add);
    if (!Grounding.numbersAppearIn(explanation, quotes)) {
      return Optional.empty();
    }
    return Optional.of(
        new Checked(
            raw.category(),
            explanation,
            leftText.orElse(null),
            added ? null : candidate.left(),
            rightText.orElse(null),
            removed ? null : candidate.right()));
  }

  private static String key(Checked difference) {
    return difference.category()
        + "|"
        + (difference.leftQuote() == null ? "" : Grounding.comparable(difference.leftQuote()))
        + "|"
        + (difference.rightQuote() == null ? "" : Grounding.comparable(difference.rightQuote()));
  }

  /**
   * Reads the model's output, which must match {@link #SCHEMA} exactly; nothing is coerced.
   *
   * @return empty when the output is malformed
   */
  Optional<List<Raw>> parse(String output) {
    JsonNode root;
    try {
      root = json.readTree(output);
    } catch (JacksonException e) {
      return Optional.empty();
    }
    if (root == null
        || !root.isObject()
        || !Set.copyOf(root.propertyNames()).equals(Set.of("differences"))
        || !root.get("differences").isArray()) {
      return Optional.empty();
    }
    List<Raw> differences = new ArrayList<>();
    for (JsonNode item : root.get("differences").values()) {
      if (!item.isObject() || !Set.copyOf(item.propertyNames()).equals(FIELDS)) {
        return Optional.empty();
      }
      JsonNode candidate = item.get("candidate");
      JsonNode category = item.get("category");
      JsonNode leftQuote = item.get("leftQuote");
      JsonNode rightQuote = item.get("rightQuote");
      JsonNode explanation = item.get("explanation");
      if (!candidate.isIntegralNumber()
          || !candidate.canConvertToInt()
          || !category.isString()
          || !leftQuote.isString()
          || !rightQuote.isString()
          || !explanation.isString()) {
        return Optional.empty();
      }
      Category parsed;
      try {
        parsed = Category.valueOf(category.stringValue());
      } catch (IllegalArgumentException e) {
        return Optional.empty();
      }
      differences.add(
          new Raw(
              candidate.intValue(),
              parsed,
              leftQuote.stringValue(),
              rightQuote.stringValue(),
              explanation.stringValue()));
    }
    return Optional.of(List.copyOf(differences));
  }

  /** The user message: the two titles, then the numbered candidates, escaped (11.2). */
  private static String message(String leftTitle, String rightTitle, Alignment alignment) {
    StringBuilder message = new StringBuilder();
    message
        .append("<documents>\n<left title=\"")
        .append(PromptAssembler.escape(leftTitle))
        .append("\"/>\n<right title=\"")
        .append(PromptAssembler.escape(rightTitle))
        .append("\"/>\n</documents>\n\n<candidates>\n");
    for (Candidate candidate : alignment.candidates()) {
      message.append("<candidate n=\"").append(candidate.n()).append("\">\n");
      if (candidate.left() != null) {
        side(message, "left", candidate.left());
      }
      if (candidate.right() != null) {
        side(message, "right", candidate.right());
      }
      message.append("</candidate>\n");
    }
    message.append("</candidates>\n\nList the real differences between LEFT and RIGHT.");
    return message.toString();
  }

  private static void side(StringBuilder message, String name, DocumentChunk chunk) {
    message
        .append('<')
        .append(name)
        .append(" pages=\"")
        .append(
            chunk.pageStart() == chunk.pageEnd()
                ? String.valueOf(chunk.pageStart())
                : chunk.pageStart() + "-" + chunk.pageEnd())
        .append("\">\n")
        .append(PromptAssembler.escape(chunk.text()))
        .append("\n</")
        .append(name)
        .append(">\n");
  }

  private static Map<Integer, DocumentChunk> byIndex(List<DocumentChunk> chunks) {
    Map<Integer, DocumentChunk> map = new HashMap<>();
    chunks.forEach(chunk -> map.put(chunk.chunkIndex(), chunk));
    return map;
  }
}
