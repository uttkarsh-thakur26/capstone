package in.edu.bennett.capstone.knowledge.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import in.edu.bennett.capstone.knowledge.FakeEmbeddingModel;
import in.edu.bennett.capstone.knowledge.TestAccounts;
import in.edu.bennett.capstone.knowledge.TestAccounts.Session;
import in.edu.bennett.capstone.knowledge.common.PgVector;
import in.edu.bennett.capstone.knowledge.document.TestDocuments;
import java.util.UUID;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Calls the summary and comparison endpoints, and builds indexed documents, for tests. */
final class TestSummaries {

  private TestSummaries() {}

  static MvcTestResult request(MockMvcTester mvc, Session session, UUID documentId) {
    return TestDocuments.send(
        mvc, session, HttpMethod.POST, "/api/v1/documents/" + documentId + "/summary", null);
  }

  static MvcTestResult get(MockMvcTester mvc, Session session, UUID documentId) {
    return TestDocuments.send(
        mvc, session, HttpMethod.GET, "/api/v1/documents/" + documentId + "/summary", null);
  }

  static MvcTestResult compare(MockMvcTester mvc, Session session, UUID left, UUID right) {
    return TestDocuments.send(
        mvc,
        session,
        HttpMethod.POST,
        "/api/v1/documents/compare",
        "{\"leftId\": \"%s\", \"rightId\": \"%s\"}".formatted(left, right));
  }

  /** Asks until the summary is no longer being made, for at most 15 seconds; returns the body. */
  static String awaitFinished(MockMvcTester mvc, Session session, UUID documentId) {
    long deadline = System.nanoTime() + 15_000_000_000L;
    while (true) {
      MvcTestResult result = get(mvc, session, documentId);
      assertThat(result).hasStatusOk();
      String body = TestAccounts.body(result);
      String status = JsonPath.read(body, "$.status");
      if (!status.equals("PENDING")) {
        return body;
      }
      if (System.nanoTime() > deadline) {
        throw new AssertionError("The summary of " + documentId + " is still being made");
      }
      try {
        Thread.sleep(25);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError(e);
      }
    }
  }

  /** Waits up to 5 seconds until the captured output holds the text, then returns. */
  static void awaitOutput(org.springframework.boot.test.system.CapturedOutput output, String text) {
    long deadline = System.nanoTime() + 5_000_000_000L;
    while (!output.getAll().contains(text)) {
      if (System.nanoTime() > deadline) {
        throw new AssertionError("The output never held: " + text);
      }
      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError(e);
      }
    }
  }

  /**
   * A document of this account, indexed, with these chunks on pages 1, 2, 3 and so on, each with
   * this many tokens and the fake embedding model's vector of its text.
   */
  static UUID indexed(
      MockMvcTester mvc,
      JdbcClient jdbc,
      Session owner,
      String title,
      String grantsJson,
      int tokensEach,
      String... chunks) {
    UUID id = TestDocuments.uploaded(mvc, owner, title, grantsJson);
    for (int i = 0; i < chunks.length; i++) {
      insertChunk(jdbc, id, i, i + 1, chunks[i], tokensEach);
    }
    jdbc.sql(
            "UPDATE document SET status = 'INDEXED', indexed_at = now(), page_count = :pages"
                + " WHERE id = :id")
        .param("id", id)
        .param("pages", Math.max(1, chunks.length))
        .update();
    return id;
  }

  static void insertChunk(
      JdbcClient jdbc, UUID documentId, int index, int page, String content, int tokens) {
    int dimensions =
        jdbc.sql(
                """
                SELECT atttypmod FROM pg_attribute
                WHERE attrelid = 'document_chunk'::regclass AND attname = 'embedding'
                """)
            .query(Integer.class)
            .single();
    jdbc.sql(
            """
            INSERT INTO document_chunk (document_id, chunk_index, page_start, page_end, content,
                token_count, embedding, created_at)
            VALUES (:id, :index, :page, :page, :content, :tokens, CAST(:embedding AS vector),
                now())
            """)
        .param("id", documentId)
        .param("index", index)
        .param("page", page)
        .param("content", content)
        .param("tokens", tokens)
        .param("embedding", PgVector.text(FakeEmbeddingModel.vectorOf(content, dimensions)))
        .update();
  }
}
