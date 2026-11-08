package in.edu.bennett.capstone.knowledge.assistant;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The structured summary asked of the chat model (SUM-2): an overview, key points, rules and
 * requirements, and important dates, each item ending with the {@code [n]} markers of the passages
 * it comes from. The same shape serves the partial summaries of the map step and the combined one.
 * Keywords are left out: keyword extraction is T3 (S23, ADR-040).
 *
 * @param overview a few sentences; empty when none survived the checks
 */
record SummaryOutput(
    String overview, List<String> keyPoints, List<String> rules, List<String> dates) {

  /** The JSON schema sent with each request, strict: every property required, no other. */
  static final String SCHEMA =
      """
      {
        "type": "object",
        "properties": {
          "overview": {"type": "string"},
          "keyPoints": {"type": "array", "items": {"type": "string"}},
          "rules": {"type": "array", "items": {"type": "string"}},
          "dates": {"type": "array", "items": {"type": "string"}}
        },
        "required": ["overview", "keyPoints", "rules", "dates"],
        "additionalProperties": false
      }""";

  private static final Set<String> FIELDS = Set.of("overview", "keyPoints", "rules", "dates");

  /**
   * Reads the model's output, which must match {@link #SCHEMA} exactly; nothing is coerced.
   *
   * @return empty when the output is malformed
   */
  static Optional<SummaryOutput> parse(JsonMapper json, String output) {
    JsonNode root;
    try {
      root = json.readTree(output);
    } catch (JacksonException e) {
      return Optional.empty();
    }
    if (root == null || !root.isObject() || !Set.copyOf(root.propertyNames()).equals(FIELDS)) {
      return Optional.empty();
    }
    JsonNode overview = root.get("overview");
    if (!overview.isString()) {
      return Optional.empty();
    }
    List<String> keyPoints = strings(root.get("keyPoints"));
    List<String> rules = strings(root.get("rules"));
    List<String> dates = strings(root.get("dates"));
    if (keyPoints == null || rules == null || dates == null) {
      return Optional.empty();
    }
    return Optional.of(new SummaryOutput(overview.stringValue(), keyPoints, rules, dates));
  }

  private static List<String> strings(JsonNode node) {
    if (node == null || !node.isArray()) {
      return null;
    }
    List<String> values = new ArrayList<>();
    for (JsonNode value : node.values()) {
      if (!value.isString()) {
        return null;
      }
      values.add(value.stringValue());
    }
    return List.copyOf(values);
  }

  /** The same summary with every item passed through the function; items it empties are dropped. */
  SummaryOutput map(UnaryOperator<String> item) {
    return new SummaryOutput(
        overview.isBlank() ? "" : item.apply(overview),
        mapAll(keyPoints, item),
        mapAll(rules, item),
        mapAll(dates, item));
  }

  private static List<String> mapAll(List<String> items, UnaryOperator<String> item) {
    return items.stream().map(item).filter(text -> !text.isBlank()).toList();
  }

  /** Every item, the overview first, in the order a reader sees them. */
  List<String> items() {
    List<String> all = new ArrayList<>();
    if (!overview.isBlank()) {
      all.add(overview);
    }
    all.addAll(keyPoints);
    all.addAll(rules);
    all.addAll(dates);
    return all;
  }

  /** At most this many items in each list. */
  SummaryOutput capped(int keyPointLimit, int ruleLimit, int dateLimit) {
    return new SummaryOutput(
        overview,
        keyPoints.stream().limit(keyPointLimit).toList(),
        rules.stream().limit(ruleLimit).toList(),
        dates.stream().limit(dateLimit).toList());
  }

  boolean isEmpty() {
    return items().isEmpty();
  }
}
