package in.edu.bennett.capstone.knowledge.assistant;

import static in.edu.bennett.capstone.knowledge.assistant.TestSummaries.awaitFinished;
import static in.edu.bennett.capstone.knowledge.assistant.TestSummaries.get;
import static in.edu.bennett.capstone.knowledge.assistant.TestSummaries.request;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import in.edu.bennett.capstone.knowledge.FakeChatModel;
import in.edu.bennett.capstone.knowledge.FakeChatModel.Request;
import in.edu.bennett.capstone.knowledge.IntegrationTest;
import in.edu.bennett.capstone.knowledge.TestAccounts;
import in.edu.bennett.capstone.knowledge.TestAccounts.Session;
import in.edu.bennett.capstone.knowledge.document.AccessMatrix;
import in.edu.bennett.capstone.knowledge.document.AccessMatrix.Access;
import in.edu.bennett.capstone.knowledge.document.AccessMatrix.Cell;
import in.edu.bennett.capstone.knowledge.document.AccessMatrix.Doc;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * The access matrix (specification 5.4, 19.2) on summaries, every one of the 30 cells. A summary
 * needs readable and indexed (ACL-4): D6, archived, is summarized, D5, failed, is refused with 409,
 * and a document the user cannot read answers 404 on both endpoints, like a missing one (ACL-8).
 * The summary of a document is shared by its readers, so a reader may find it already made; it is
 * served only after the check, and its prompt holds that document's text alone.
 */
@IntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SummaryAccessMatrixTest {

  @Autowired MockMvcTester mvc;

  @Autowired JdbcClient jdbc;

  @Autowired FakeChatModel chat;

  private AccessMatrix matrix;

  @BeforeAll
  void buildTheFixture() {
    matrix = AccessMatrix.build(mvc, jdbc);
  }

  /** The fake chat model is shared by every test class; the next one starts from empty. */
  @AfterAll
  void resetTheModel() {
    chat.reset();
  }

  Stream<Cell> cells() {
    return AccessMatrix.cells();
  }

  @ParameterizedTest(name = "summary: {0}")
  @MethodSource("cells")
  void aSummaryFollowsTheMatrix(Cell cell) {
    Session session = matrix.session(cell.user());
    UUID id = matrix.id(cell.doc());
    Access expected = matrix.expected(cell);

    MvcTestResult requested = request(mvc, session, id);
    MvcTestResult read = get(mvc, session, id);

    if (!expected.readable()) {
      for (MvcTestResult result : List.of(requested, read)) {
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
        assertThat(TestAccounts.body(result)).doesNotContain(matrix.chunkText(cell.doc()));
      }
      return;
    }
    if (cell.doc() == Doc.D5) {
      assertThat(requested).hasStatus(HttpStatus.CONFLICT);
      assertThat(requested).bodyJson().extractingPath("$.code").isEqualTo("DOCUMENT_NOT_INDEXED");
      assertThat(read).hasStatusOk();
      assertThat(read).bodyJson().extractingPath("$.status").isEqualTo("NONE");
      return;
    }
    assertThat(requested.getResponse().getStatus()).isIn(200, 202);
    String body = awaitFinished(mvc, session, id);
    assertThat((String) JsonPath.read(body, "$.status")).isEqualTo("READY");
    List<Map<String, Object>> citations = JsonPath.read(body, "$.summary.citations");
    assertThat(citations)
        .isNotEmpty()
        .allSatisfy(
            c -> {
              assertThat(c.get("documentId")).isEqualTo(id.toString());
              assertThat((String) c.get("snippet")).isEqualTo(matrix.chunkText(cell.doc()));
            });
    // RAG-1 for summaries: the call that made it saw this document's text and no other's.
    Request made =
        chat.requests().stream()
            .filter(r -> r.user().contains(matrix.chunkText(cell.doc())))
            .findFirst()
            .orElseThrow();
    for (Doc other : Doc.values()) {
      if (other != cell.doc()) {
        assertThat(made.user()).doesNotContain(matrix.chunkText(other));
      }
    }
  }
}
