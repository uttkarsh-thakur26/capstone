package in.edu.bennett.capstone.knowledge.document;

import static in.edu.bennett.capstone.knowledge.document.AccessMatrix.Access.NONE;
import static in.edu.bennett.capstone.knowledge.document.AccessMatrix.Access.READ;
import static in.edu.bennett.capstone.knowledge.document.AccessMatrix.Access.READ_NOT_RETRIEVABLE;
import static org.assertj.core.api.Assertions.assertThat;

import in.edu.bennett.capstone.knowledge.TestAccounts;
import in.edu.bennett.capstone.knowledge.TestAccounts.Account;
import in.edu.bennett.capstone.knowledge.TestAccounts.Session;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * The access-matrix fixture of specification 5.4, as corrected by ADR-030, built through the real
 * endpoints. Every phase asserts every cell on every access path it adds (19.2): detail, list and
 * download from Phase 2, search and question answering later.
 *
 * <p>The test database is shared by all integration tests, so the fixture creates its own accounts
 * and puts a marker in every title; a list query scoped by {@link #marker()} sees only these six
 * documents. {@code INDEXED} and {@code FAILED} are set directly in the database, because nothing
 * processes documents yet; the archive of D6 goes through the API. D5's leftover chunks (5.4) are
 * added here once {@code document_chunk} exists (Phase 3).
 */
public final class AccessMatrix {

  public enum User {
    ADMIN,
    FACULTY_A,
    FACULTY_B,
    STUDENT_S1,
    STUDENT_S2
  }

  public enum Doc {
    D1,
    D2,
    D3,
    D4,
    D5,
    D6
  }

  /** One cell of the matrix. */
  public enum Access {
    /** Not readable: 404 and absent everywhere (ACL-8). */
    NONE,
    /** Readable and retrievable. */
    READ,
    /** Detail, list and download work; search and question answering exclude it (ACL-4). */
    READ_NOT_RETRIEVABLE;

    public boolean readable() {
      return this != NONE;
    }

    public boolean retrievable() {
      return this == READ;
    }
  }

  /** A user and a document: one cell. */
  public record Cell(User user, Doc doc) {

    @Override
    public String toString() {
      return user + " on " + doc;
    }
  }

  /** Specification 5.4 with ADR-030, row by row: Admin, Faculty A, Faculty B, S1, S2. */
  private static final Map<Doc, List<Access>> EXPECTED =
      Map.of(
          Doc.D1, List.of(READ, READ, NONE, NONE, NONE),
          Doc.D2, List.of(READ, READ, NONE, READ, READ),
          Doc.D3, List.of(READ, READ, NONE, READ, NONE),
          Doc.D4, List.of(READ, READ, READ, NONE, NONE),
          Doc.D5,
              List.of(
                  READ_NOT_RETRIEVABLE,
                  READ_NOT_RETRIEVABLE,
                  NONE,
                  READ_NOT_RETRIEVABLE,
                  READ_NOT_RETRIEVABLE),
          Doc.D6,
              List.of(
                  READ_NOT_RETRIEVABLE,
                  READ_NOT_RETRIEVABLE,
                  NONE,
                  READ_NOT_RETRIEVABLE,
                  READ_NOT_RETRIEVABLE));

  private final String marker;
  private final Map<User, Account> accounts;
  private final Map<User, Session> sessions;
  private final Map<Doc, UUID> documents;
  private final Map<Doc, String> grants;

  private AccessMatrix(
      String marker,
      Map<User, Account> accounts,
      Map<User, Session> sessions,
      Map<Doc, UUID> documents,
      Map<Doc, String> grants) {
    this.marker = marker;
    this.accounts = accounts;
    this.sessions = sessions;
    this.documents = documents;
    this.grants = grants;
  }

  /** Creates the five accounts and the six documents. */
  public static AccessMatrix build(MockMvcTester mvc, JdbcClient jdbc) {
    String marker = "matrix-" + UUID.randomUUID();
    Map<User, Account> accounts = new EnumMap<>(User.class);
    accounts.put(User.ADMIN, TestAccounts.registerAs(mvc, jdbc, "ADMIN"));
    accounts.put(User.FACULTY_A, TestAccounts.registerAs(mvc, jdbc, "FACULTY"));
    accounts.put(User.FACULTY_B, TestAccounts.registerAs(mvc, jdbc, "FACULTY"));
    accounts.put(User.STUDENT_S1, TestAccounts.registerStudent(mvc));
    accounts.put(User.STUDENT_S2, TestAccounts.registerStudent(mvc));
    Map<User, Session> sessions = new EnumMap<>(User.class);
    accounts.forEach((user, account) -> sessions.put(user, TestAccounts.login(mvc, account)));

    String toStudents = "[{\"type\": \"ROLE\", \"role\": \"STUDENT\"}]";
    Map<Doc, String> grants = new EnumMap<>(Doc.class);
    grants.put(Doc.D1, "[]");
    grants.put(Doc.D2, toStudents);
    grants.put(
        Doc.D3,
        "[{\"type\": \"USER\", \"userId\": \"%s\"}]".formatted(accounts.get(User.STUDENT_S1).id()));
    grants.put(Doc.D4, "[{\"type\": \"ROLE\", \"role\": \"FACULTY\"}]");
    grants.put(Doc.D5, toStudents);
    grants.put(Doc.D6, toStudents);

    Map<Doc, UUID> documents = new EnumMap<>(Doc.class);
    for (Doc doc : Doc.values()) {
      Session owner = sessions.get(doc == Doc.D4 ? User.FACULTY_B : User.FACULTY_A);
      documents.put(doc, TestDocuments.uploaded(mvc, owner, doc + " " + marker, grants.get(doc)));
    }
    for (Doc doc : List.of(Doc.D1, Doc.D2, Doc.D3, Doc.D4, Doc.D6)) {
      jdbc.sql("UPDATE document SET status = 'INDEXED', indexed_at = now() WHERE id = :id")
          .param("id", documents.get(doc))
          .update();
    }
    jdbc.sql(
            "UPDATE document SET status = 'FAILED', failure_code = 'EXTRACTION_ERROR' WHERE id = :id")
        .param("id", documents.get(Doc.D5))
        .update();
    assertThat(
            TestDocuments.send(
                mvc,
                sessions.get(User.FACULTY_A),
                HttpMethod.PATCH,
                "/api/v1/documents/" + documents.get(Doc.D6),
                "{\"archived\": true}"))
        .hasStatusOk();
    return new AccessMatrix(marker, accounts, sessions, documents, grants);
  }

  /** Every cell, for parameterized tests. */
  public static Stream<Cell> cells() {
    return Stream.of(User.values())
        .flatMap(user -> Stream.of(Doc.values()).map(doc -> new Cell(user, doc)));
  }

  public Access expected(Cell cell) {
    return EXPECTED.get(cell.doc()).get(cell.user().ordinal());
  }

  /** ACL-2 with ADR-030: admins, and the owner while FACULTY. Faculty A owns all but D4. */
  public boolean canManage(Cell cell) {
    User owner = cell.doc() == Doc.D4 ? User.FACULTY_B : User.FACULTY_A;
    return cell.user() == User.ADMIN || cell.user() == owner;
  }

  /** The documents this user can read, in the order of the enum. */
  public List<Doc> readableBy(User user) {
    return Stream.of(Doc.values()).filter(doc -> expected(new Cell(user, doc)).readable()).toList();
  }

  public Session session(User user) {
    return sessions.get(user);
  }

  public Account account(User user) {
    return accounts.get(user);
  }

  public UUID id(Doc doc) {
    return documents.get(doc);
  }

  /** The document's grants as the JSON array they were created with. */
  public String grantsJson(Doc doc) {
    return grants.get(doc);
  }

  /** Part of every fixture title: a list query for it returns only the six fixture documents. */
  public String marker() {
    return marker;
  }
}
