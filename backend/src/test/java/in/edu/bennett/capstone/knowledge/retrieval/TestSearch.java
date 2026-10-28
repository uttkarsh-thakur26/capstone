package in.edu.bennett.capstone.knowledge.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import in.edu.bennett.capstone.knowledge.TestAccounts;
import in.edu.bennett.capstone.knowledge.TestAccounts.Session;
import in.edu.bennett.capstone.knowledge.document.TestDocuments;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Calls {@code POST /search} through the real endpoint, for integration tests. */
public final class TestSearch {

  private TestSearch() {}

  /** One result as the client sees it. */
  public record Hit(UUID documentId, int pageStart, int pageEnd, String text, double score) {}

  /** A search with these settings; {@code k} is left to the default when null. */
  public static MvcTestResult search(
      MockMvcTester mvc,
      Session session,
      SearchMode mode,
      String query,
      Integer k,
      Collection<UUID> documentIds) {
    StringBuilder json =
        new StringBuilder("{\"mode\": \"")
            .append(mode)
            .append("\", \"query\": ")
            .append(quoted(query));
    if (k != null) {
      json.append(", \"k\": ").append(k);
    }
    if (documentIds != null) {
      json.append(", \"documentIds\": ")
          .append(
              documentIds.stream()
                  .map(id -> "\"" + id + "\"")
                  .collect(Collectors.joining(",", "[", "]")));
    }
    return TestDocuments.send(
        mvc, session, HttpMethod.POST, "/api/v1/search", json.append('}').toString());
  }

  /** The results of a search that must succeed, best first. */
  public static List<Hit> hits(
      MockMvcTester mvc, Session session, SearchMode mode, String query, Integer k) {
    return hits(search(mvc, session, mode, query, k, null));
  }

  public static List<Hit> hits(MvcTestResult result) {
    assertThat(result).hasStatusOk();
    List<Map<String, Object>> results = JsonPath.read(TestAccounts.body(result), "$.results");
    return results.stream()
        .map(
            r ->
                new Hit(
                    UUID.fromString((String) r.get("documentId")),
                    (Integer) r.get("pageStart"),
                    (Integer) r.get("pageEnd"),
                    (String) r.get("text"),
                    ((Number) r.get("score")).doubleValue()))
        .toList();
  }

  public static List<UUID> documentIds(List<Hit> hits) {
    return hits.stream().map(Hit::documentId).toList();
  }

  /** A JSON string literal. */
  public static String quoted(String text) {
    StringBuilder json = new StringBuilder("\"");
    for (char c : text.toCharArray()) {
      switch (c) {
        case '"' -> json.append("\\\"");
        case '\\' -> json.append("\\\\");
        case '\n' -> json.append("\\n");
        default -> {
          if (c < 0x20) {
            json.append("\\u%04x".formatted((int) c));
          } else {
            json.append(c);
          }
        }
      }
    }
    return json.append('"').toString();
  }
}
