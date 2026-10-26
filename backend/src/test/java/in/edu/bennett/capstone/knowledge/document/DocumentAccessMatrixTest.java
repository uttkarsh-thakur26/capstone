package in.edu.bennett.capstone.knowledge.document;

import static in.edu.bennett.capstone.knowledge.TestAccounts.body;
import static in.edu.bennett.capstone.knowledge.document.TestDocuments.get;
import static in.edu.bennett.capstone.knowledge.document.TestDocuments.send;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import in.edu.bennett.capstone.knowledge.IntegrationTest;
import in.edu.bennett.capstone.knowledge.TestAccounts;
import in.edu.bennett.capstone.knowledge.TestAccounts.Session;
import in.edu.bennett.capstone.knowledge.document.AccessMatrix.Cell;
import in.edu.bennett.capstone.knowledge.document.AccessMatrix.Doc;
import in.edu.bennett.capstone.knowledge.document.AccessMatrix.User;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * The access matrix (specification 5.4, 19.2, highest priority) on every path that exists in Phase
 * 2: detail, list and download, and grant management, which follows ACL-2. Each of the 30 cells is
 * its own test case, so a failure names the user and the document.
 */
@IntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DocumentAccessMatrixTest {

  @Autowired MockMvcTester mvc;

  @Autowired JdbcClient jdbc;

  private AccessMatrix matrix;

  @BeforeAll
  void buildTheFixture() {
    matrix = AccessMatrix.build(mvc, jdbc);
  }

  Stream<Cell> cells() {
    return AccessMatrix.cells();
  }

  @ParameterizedTest(name = "detail: {0}")
  @MethodSource("cells")
  void detail(Cell cell) {
    MvcTestResult result =
        get(mvc, matrix.session(cell.user()), "/api/v1/documents/" + matrix.id(cell.doc()));

    if (matrix.expected(cell).readable()) {
      assertThat(result).hasStatusOk();
      assertThat(result)
          .bodyJson()
          .extractingPath("$.id")
          .isEqualTo(matrix.id(cell.doc()).toString());
      assertThat(result).bodyJson().extractingPath("$.canManage").isEqualTo(matrix.canManage(cell));
    } else {
      assertNotFound(result);
    }
  }

  @ParameterizedTest(name = "download: {0}")
  @MethodSource("cells")
  void download(Cell cell) {
    MvcTestResult result =
        get(
            mvc,
            matrix.session(cell.user()),
            "/api/v1/documents/" + matrix.id(cell.doc()) + "/content");

    if (matrix.expected(cell).readable()) {
      assertThat(result).hasStatusOk();
      assertThat(result.getResponse().getContentAsByteArray()).startsWith('%', 'P', 'D', 'F', '-');
    } else {
      assertNotFound(result);
    }
  }

  /** ACL-2: grants are read and replaced by managers; readers get 403, everyone else 404. */
  @ParameterizedTest(name = "grants: {0}")
  @MethodSource("cells")
  void grantManagement(Cell cell) {
    Session session = matrix.session(cell.user());
    String uri = "/api/v1/documents/" + matrix.id(cell.doc()) + "/grants";

    MvcTestResult read = get(mvc, session, uri);
    // The same set again: allowed for a manager and changes nothing for the other cells.
    MvcTestResult replace =
        send(
            mvc,
            session,
            HttpMethod.PUT,
            uri,
            "{\"grants\": %s}".formatted(matrix.grantsJson(cell.doc())));

    HttpStatus expected =
        matrix.canManage(cell)
            ? HttpStatus.OK
            : matrix.expected(cell).readable() ? HttpStatus.FORBIDDEN : HttpStatus.NOT_FOUND;
    assertThat(read).hasStatus(expected);
    assertThat(replace).hasStatus(expected);
  }

  /**
   * DOC-4, ACL-E2: the list holds exactly the readable fixture documents, and the total counts only
   * those, on every page size.
   */
  @ParameterizedTest(name = "list: {0}")
  @EnumSource(User.class)
  void list(User user) {
    List<UUID> expected = matrix.readableBy(user).stream().map(matrix::id).toList();

    MvcTestResult all = list(user, 100);
    MvcTestResult onePerPage = list(user, 1);

    assertThat(all).hasStatusOk();
    List<String> ids = JsonPath.read(body(all), "$.items[*].id");
    assertThat(ids.stream().map(UUID::fromString).toList())
        .containsExactlyInAnyOrderElementsOf(expected);
    assertThat(all).bodyJson().extractingPath("$.totalItems").isEqualTo(expected.size());
    assertThat(onePerPage).bodyJson().extractingPath("$.totalItems").isEqualTo(expected.size());
    assertThat(onePerPage)
        .bodyJson()
        .extractingPath("$.items")
        .asArray()
        .hasSize(Math.min(1, expected.size()));
  }

  /** ACL-8: an unreadable document answers exactly like an id that was never used. */
  @Test
  void anUnreadableDocumentCannotBeToldApartFromAMissingOne() {
    Session facultyB = matrix.session(User.FACULTY_B);

    MvcTestResult unreadable = get(mvc, facultyB, "/api/v1/documents/" + matrix.id(Doc.D1));
    MvcTestResult missing = get(mvc, facultyB, "/api/v1/documents/" + UUID.randomUUID());

    assertThat(withoutInstance(body(unreadable))).isEqualTo(withoutInstance(body(missing)));
  }

  /** 5.4: a disabled user cannot authenticate, so every cell is "no", on every path. */
  @Test
  void aDisabledUserGetsNothing() {
    TestAccounts.Account student = TestAccounts.registerStudent(mvc);
    Session session = TestAccounts.login(mvc, student);
    assertThat(get(mvc, session, "/api/v1/documents/" + matrix.id(Doc.D2))).hasStatusOk();

    jdbc.sql("UPDATE app_user SET enabled = false WHERE id = :id")
        .param("id", student.id())
        .update();

    for (Doc doc : Doc.values()) {
      String uri = "/api/v1/documents/" + matrix.id(doc);
      assertThat(get(mvc, session, uri)).hasStatus(HttpStatus.UNAUTHORIZED);
      assertThat(get(mvc, session, uri + "/content")).hasStatus(HttpStatus.UNAUTHORIZED);
      assertThat(get(mvc, session, uri + "/grants")).hasStatus(HttpStatus.UNAUTHORIZED);
    }
    assertThat(get(mvc, session, "/api/v1/documents?query=" + matrix.marker()))
        .hasStatus(HttpStatus.UNAUTHORIZED);
  }

  private MvcTestResult list(User user, int size) {
    return get(
        mvc, matrix.session(user), "/api/v1/documents?query=" + matrix.marker() + "&size=" + size);
  }

  private static void assertNotFound(MvcTestResult result) {
    assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
  }

  /** The Problem Details body without {@code instance}, which repeats the request path. */
  private static String withoutInstance(String json) {
    return json.replaceAll("\"instance\"\\s*:\\s*\"[^\"]*\",?", "");
  }
}
