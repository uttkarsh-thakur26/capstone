package in.edu.bennett.capstone.knowledge.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import in.edu.bennett.capstone.knowledge.IntegrationTest;
import in.edu.bennett.capstone.knowledge.TestAccounts.Session;
import in.edu.bennett.capstone.knowledge.document.AccessMatrix;
import in.edu.bennett.capstone.knowledge.document.AccessMatrix.Access;
import in.edu.bennett.capstone.knowledge.document.AccessMatrix.Cell;
import in.edu.bennett.capstone.knowledge.document.AccessMatrix.Doc;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
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
 * The access matrix (specification 5.4, 19.2) on search, in both modes: every one of the 30 cells
 * is a test case per mode, unscoped and scoped to the document. "Read, not retrievable" (D5 failed
 * with a leftover chunk, D6 archived) means search excludes the document while detail, list and
 * download still work ({@code DocumentAccessMatrixTest}). This is the test of the native SQL
 * predicate (ACL-E3).
 */
@IntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SearchAccessMatrixTest {

  @Autowired MockMvcTester mvc;

  @Autowired JdbcClient jdbc;

  private AccessMatrix matrix;

  @BeforeAll
  void buildTheFixture() {
    matrix = AccessMatrix.build(mvc, jdbc);
  }

  /** One cell in one mode. */
  record Case(Cell cell, SearchMode mode) {

    @Override
    public String toString() {
      return mode + ", " + cell;
    }
  }

  Stream<Case> cases() {
    return AccessMatrix.cells()
        .flatMap(cell -> Stream.of(SearchMode.values()).map(mode -> new Case(cell, mode)));
  }

  /**
   * The query is the document's own chunk text, so that chunk is the best match in both modes: it
   * comes first when the user may retrieve it, and is absent otherwise, however well it matches. No
   * other fixture document the user may not retrieve appears either.
   */
  @ParameterizedTest(name = "search: {0}")
  @MethodSource("cases")
  void searchReturnsTheDocumentOnlyWhenItIsRetrievable(Case c) {
    Session session = matrix.session(c.cell().user());
    List<UUID> found =
        TestSearch.documentIds(
            TestSearch.hits(mvc, session, c.mode(), matrix.chunkText(c.cell().doc()), 6));

    UUID id = matrix.id(c.cell().doc());
    if (matrix.expected(c.cell()).retrievable()) {
      assertThat(found).first().isEqualTo(id);
    } else {
      assertThat(found).doesNotContain(id);
    }
    for (Doc other : Doc.values()) {
      if (!matrix.expected(new Cell(c.cell().user(), other)).retrievable()) {
        assertThat(found).as("results include %s", other).doesNotContain(matrix.id(other));
      }
    }
  }

  /**
   * RET-8: scoped to the document, an unreadable one answers 404 like a missing one, a readable one
   * that is failed or archived is accepted and contributes nothing, and a retrievable one gives its
   * chunk only.
   */
  @ParameterizedTest(name = "scoped search: {0}")
  @MethodSource("cases")
  void searchScopedToTheDocumentFollowsTheMatrix(Case c) {
    Session session = matrix.session(c.cell().user());
    UUID id = matrix.id(c.cell().doc());
    MvcTestResult result =
        TestSearch.search(mvc, session, c.mode(), matrix.chunkText(c.cell().doc()), 6, List.of(id));

    Access expected = matrix.expected(c.cell());
    if (!expected.readable()) {
      assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
      assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
    } else if (expected.retrievable()) {
      assertThat(TestSearch.documentIds(TestSearch.hits(result))).containsExactly(id);
    } else {
      assertThat(TestSearch.hits(result)).isEmpty();
    }
  }
}
