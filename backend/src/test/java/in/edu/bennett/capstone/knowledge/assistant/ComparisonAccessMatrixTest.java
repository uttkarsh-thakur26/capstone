package in.edu.bennett.capstone.knowledge.assistant;

import static in.edu.bennett.capstone.knowledge.assistant.TestSummaries.compare;
import static org.assertj.core.api.Assertions.assertThat;

import in.edu.bennett.capstone.knowledge.FakeChatModel;
import in.edu.bennett.capstone.knowledge.IntegrationTest;
import in.edu.bennett.capstone.knowledge.TestAccounts;
import in.edu.bennett.capstone.knowledge.TestAccounts.Session;
import in.edu.bennett.capstone.knowledge.document.AccessMatrix;
import in.edu.bennett.capstone.knowledge.document.AccessMatrix.Access;
import in.edu.bennett.capstone.knowledge.document.AccessMatrix.Cell;
import in.edu.bennett.capstone.knowledge.document.AccessMatrix.Doc;
import in.edu.bennett.capstone.knowledge.document.AccessMatrix.User;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * The access matrix (specification 5.4, 19.2) on comparison, every one of the 30 cells, with the
 * document on the left and on the right. The other side is a reference document the user can read,
 * shared with that user alone, so the cell under test decides the outcome (CMP-1): unreadable
 * answers 404 whichever side it is on (ACL-8), failed (D5) answers 409, and archived (D6) is
 * compared. The one model call sees the two documents' text and no other's (RAG-1).
 */
@IntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ComparisonAccessMatrixTest {

  @Autowired MockMvcTester mvc;

  @Autowired JdbcClient jdbc;

  @Autowired FakeChatModel chat;

  private AccessMatrix matrix;
  private final Map<User, UUID> references = new EnumMap<>(User.class);
  private final Map<User, String> referenceTexts = new EnumMap<>(User.class);

  @BeforeAll
  void buildTheFixture() {
    matrix = AccessMatrix.build(mvc, jdbc);
    Session admin = matrix.session(User.ADMIN);
    for (User user : User.values()) {
      String grants =
          user == User.ADMIN
              ? "[]"
              : "[{\"type\": \"USER\", \"userId\": \"%s\"}]".formatted(matrix.account(user).id());
      String text = "reference passage for " + user + " " + UUID.randomUUID();
      references.put(
          user,
          TestSummaries.indexed(mvc, jdbc, admin, "Reference " + matrix.marker(), grants, 8, text));
      referenceTexts.put(user, text);
    }
  }

  /**
   * The fake chat model is shared by every test class of the context; it is emptied before and
   * after each test, so calls made by another class never count here, whatever the class order.
   */
  @BeforeEach
  @AfterEach
  void resetTheModel() {
    chat.reset();
  }

  /** One cell, with the document on one side. */
  record Case(Cell cell, boolean onTheLeft) {

    @Override
    public String toString() {
      return cell + (onTheLeft ? ", on the left" : ", on the right");
    }
  }

  Stream<Case> cases() {
    return AccessMatrix.cells()
        .flatMap(cell -> Stream.of(new Case(cell, true), new Case(cell, false)));
  }

  @ParameterizedTest(name = "compare: {0}")
  @MethodSource("cases")
  void aComparisonFollowsTheMatrix(Case c) {
    Session session = matrix.session(c.cell().user());
    UUID document = matrix.id(c.cell().doc());
    UUID reference = references.get(c.cell().user());
    Access expected = matrix.expected(c.cell());

    MvcTestResult result =
        c.onTheLeft()
            ? compare(mvc, session, document, reference)
            : compare(mvc, session, reference, document);

    if (!expected.readable()) {
      assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
      assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
      assertThat(TestAccounts.body(result)).doesNotContain(matrix.chunkText(c.cell().doc()));
      assertThat(chat.calls()).isZero();
    } else if (c.cell().doc() == Doc.D5) {
      assertThat(result).hasStatus(HttpStatus.CONFLICT);
      assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("DOCUMENT_NOT_INDEXED");
      assertThat(chat.calls()).isZero();
    } else {
      assertThat(result).hasStatusOk();
      assertThat(chat.calls()).isOne();
      String prompt = chat.requests().getFirst().user();
      assertThat(prompt)
          .contains(matrix.chunkText(c.cell().doc()))
          .contains(referenceTexts.get(c.cell().user()));
      for (Doc other : Doc.values()) {
        if (other != c.cell().doc()) {
          assertThat(prompt).doesNotContain(matrix.chunkText(other));
        }
      }
    }
  }
}
