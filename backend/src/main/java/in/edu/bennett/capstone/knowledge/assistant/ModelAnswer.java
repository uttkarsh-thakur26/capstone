package in.edu.bennett.capstone.knowledge.assistant;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The structured output asked of the chat model (11.1 step 7): whether the sources answer the
 * question, the answer with {@code [n]} markers, and the numbers of the sources it uses. Titles,
 * pages and snippets are never part of it (RAG-3).
 */
record ModelAnswer(boolean answerable, String answer, List<Integer> citations) {

  /**
   * The JSON schema sent with each request. Every property is required and no other is allowed,
   * which is what OpenAI's strict mode expects.
   */
  static final String SCHEMA =
      """
      {
        "type": "object",
        "properties": {
          "answerable": {"type": "boolean"},
          "answer": {"type": "string"},
          "citations": {"type": "array", "items": {"type": "integer"}}
        },
        "required": ["answerable", "answer", "citations"],
        "additionalProperties": false
      }""";

  private static final Set<String> FIELDS = Set.of("answerable", "answer", "citations");

  /**
   * Reads the model's output, which must match {@link #SCHEMA} exactly: a missing or extra field, a
   * value of the wrong type, or text that is not JSON makes the output malformed. Nothing is
   * coerced, so a model that drifted from the schema is never taken at its word.
   *
   * @return empty when the output is malformed
   */
  static Optional<ModelAnswer> parse(JsonMapper json, String output) {
    JsonNode root;
    try {
      root = json.readTree(output);
    } catch (JacksonException e) {
      return Optional.empty();
    }
    if (root == null || !root.isObject() || !Set.copyOf(root.propertyNames()).equals(FIELDS)) {
      return Optional.empty();
    }
    JsonNode answerable = root.get("answerable");
    JsonNode answer = root.get("answer");
    JsonNode citations = root.get("citations");
    if (!answerable.isBoolean() || !answer.isString() || !citations.isArray()) {
      return Optional.empty();
    }
    List<Integer> numbers = new ArrayList<>();
    for (JsonNode number : citations.values()) {
      if (!number.isIntegralNumber() || !number.canConvertToInt()) {
        return Optional.empty();
      }
      numbers.add(number.intValue());
    }
    return Optional.of(
        new ModelAnswer(answerable.booleanValue(), answer.stringValue(), List.copyOf(numbers)));
  }
}
