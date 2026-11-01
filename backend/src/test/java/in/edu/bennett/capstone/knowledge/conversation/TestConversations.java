package in.edu.bennett.capstone.knowledge.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import in.edu.bennett.capstone.knowledge.TestAccounts;
import in.edu.bennett.capstone.knowledge.TestAccounts.Session;
import in.edu.bennett.capstone.knowledge.document.TestDocuments;
import in.edu.bennett.capstone.knowledge.retrieval.TestSearch;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Creates conversations and asks in them through the real endpoints, for integration tests. */
public final class TestConversations {

  private TestConversations() {}

  /** One citation as the client sees it. */
  public record Cited(
      int ordinal,
      UUID documentId,
      String documentTitle,
      int pageStart,
      int pageEnd,
      String snippet) {}

  /** An answer as the client sees it. */
  public record Reply(UUID messageId, String outcome, String answer, List<Cited> citations) {}

  public static UUID create(MockMvcTester mvc, Session session) {
    MvcTestResult result =
        TestDocuments.send(mvc, session, HttpMethod.POST, "/api/v1/conversations", null);
    assertThat(result).hasStatus(HttpStatus.CREATED);
    return UUID.fromString(JsonPath.read(TestAccounts.body(result), "$.id"));
  }

  /** {@code POST /conversations/{id}/messages}; no scope when {@code documentIds} is null. */
  public static MvcTestResult ask(
      MockMvcTester mvc,
      Session session,
      UUID conversationId,
      String question,
      Collection<UUID> documentIds) {
    StringBuilder json = new StringBuilder("{\"question\": ").append(TestSearch.quoted(question));
    if (documentIds != null) {
      json.append(", \"documentIds\": ")
          .append(
              documentIds.stream()
                  .map(id -> "\"" + id + "\"")
                  .collect(Collectors.joining(",", "[", "]")));
    }
    return TestDocuments.send(
        mvc,
        session,
        HttpMethod.POST,
        "/api/v1/conversations/" + conversationId + "/messages",
        json.append('}').toString());
  }

  /** Asks in a new conversation; the answer must be a 200. */
  public static Reply answer(MockMvcTester mvc, Session session, String question) {
    return reply(ask(mvc, session, create(mvc, session), question, null));
  }

  public static Reply reply(MvcTestResult result) {
    assertThat(result).hasStatusOk();
    String body = TestAccounts.body(result);
    List<Map<String, Object>> citations = JsonPath.read(body, "$.citations");
    return new Reply(
        UUID.fromString(JsonPath.read(body, "$.messageId")),
        JsonPath.read(body, "$.outcome"),
        JsonPath.read(body, "$.answer"),
        citations.stream()
            .map(
                c ->
                    new Cited(
                        (Integer) c.get("ordinal"),
                        UUID.fromString((String) c.get("documentId")),
                        (String) c.get("documentTitle"),
                        (Integer) c.get("pageStart"),
                        (Integer) c.get("pageEnd"),
                        (String) c.get("snippet")))
            .toList());
  }
}
