package in.edu.bennett.capstone.knowledge.assistant;

import in.edu.bennett.capstone.knowledge.ai.ChatGenerator;
import in.edu.bennett.capstone.knowledge.ai.ChatResult;
import in.edu.bennett.capstone.knowledge.assistant.Answer.Turn;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Follow-up handling, 11.1 step 3: the earlier turns plus the new question become one standalone
 * search query, so that "and how are its marks divided?" retrieves the passage about the lab exam's
 * marks. One model call at temperature 0, with its own versioned prompt. Only retrieval uses the
 * result; the model still answers the user's own question, with the history beside it.
 */
class FollowUpRewriter {

  private static final Logger log = LoggerFactory.getLogger(FollowUpRewriter.class);

  static final String VERSION = "condense-v1";

  /** One required string, no other field: what OpenAI's strict mode expects. */
  static final String SCHEMA =
      """
      {
        "type": "object",
        "properties": {"query": {"type": "string"}},
        "required": ["query"],
        "additionalProperties": false
      }""";

  /** As long as a question may be (11.1 step 2), so the query stays within the embedder's limit. */
  private static final int MAX_QUERY_LENGTH = 1000;

  /**
   * @param query what retrieval searches for
   * @param result the rewrite call, null when none was made
   */
  record Rewrite(String query, ChatResult result) {}

  private final ChatGenerator generator;
  private final PromptAssembler assembler;
  private final JsonMapper json;
  private final String prompt;

  FollowUpRewriter(ChatGenerator generator, PromptAssembler assembler, JsonMapper json) {
    this.generator = generator;
    this.assembler = assembler;
    this.json = json;
    this.prompt = SystemPrompt.load(VERSION);
  }

  /**
   * The question unchanged when there is no history; otherwise the model's standalone query. Output
   * that is malformed, empty or too long is not trusted, and the question is searched as asked.
   *
   * @param history the turns that go to the model, already filtered and fitted
   * @throws in.edu.bennett.capstone.knowledge.ai.ChatFailedException when the model gives no
   *     answer: the answer would fail the same way, so the question ends here instead of waiting
   *     twice
   */
  Rewrite rewrite(List<Turn> history, String question) {
    if (history.isEmpty()) {
      return new Rewrite(question, null);
    }
    ChatResult result =
        generator.generate(prompt, assembler.followUp(history, question), SCHEMA, 0.0);
    String query = query(result.text());
    if (query == null) {
      log.warn("The follow-up rewrite gave unusable output; the question is searched as asked");
      return new Rewrite(question, result);
    }
    return new Rewrite(query, result);
  }

  private String query(String output) {
    JsonNode root;
    try {
      root = json.readTree(output);
    } catch (JacksonException e) {
      return null;
    }
    if (root == null
        || !root.isObject()
        || !Set.copyOf(root.propertyNames()).equals(Set.of("query"))
        || !root.get("query").isString()) {
      return null;
    }
    String query = root.get("query").stringValue().strip();
    return query.isEmpty() || query.length() > MAX_QUERY_LENGTH ? null : query;
  }
}
