package in.edu.bennett.capstone.knowledge.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import in.edu.bennett.capstone.knowledge.FakeChatModel;
import in.edu.bennett.capstone.knowledge.IntegrationTest;
import in.edu.bennett.capstone.knowledge.TestAccounts.Session;
import in.edu.bennett.capstone.knowledge.document.AccessMatrix;
import in.edu.bennett.capstone.knowledge.document.AccessMatrix.Access;
import in.edu.bennett.capstone.knowledge.document.AccessMatrix.Cell;
import in.edu.bennett.capstone.knowledge.document.AccessMatrix.Doc;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
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
 * The access matrix (specification 5.4, 19.2) on question answering: for every one of the 30 cells,
 * the document's chunk reaches the chat model only when the user may retrieve it, and no chunk of a
 * document the user may not retrieve ever does (RAG-1). Scoped to the document, an unreadable one
 * answers 404 (RET-8), and a readable one that is failed or archived contributes nothing, so the
 * model is not called.
 */
@IntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AnswerAccessMatrixTest {

  @Autowired MockMvcTester mvc;

  @Autowired JdbcClient jdbc;

  @Autowired FakeChatModel chat;

  private AccessMatrix matrix;

  @BeforeAll
  void buildTheFixture() {
    matrix = AccessMatrix.build(mvc, jdbc);
  }

  @AfterEach
  void resetTheChatModel() {
    chat.reset();
  }

  Stream<Cell> cells() {
    return AccessMatrix.cells();
  }

  /** The question is the document's own chunk text, so that chunk is its best match. */
  @ParameterizedTest(name = "ask: {0}")
  @MethodSource("cells")
  void theModelSeesTheDocumentOnlyWhenItIsRetrievable(Cell cell) {
    Session session = matrix.session(cell.user());

    TestConversations.reply(
        TestConversations.ask(
            mvc,
            session,
            TestConversations.create(mvc, session),
            matrix.chunkText(cell.doc()),
            null));

    String prompt = chat.requests().isEmpty() ? "" : sources(chat.requests().getFirst().user());
    if (matrix.expected(cell).retrievable()) {
      assertThat(prompt).contains(matrix.chunkText(cell.doc()));
    } else {
      assertThat(prompt).doesNotContain(matrix.chunkText(cell.doc()));
    }
    for (Doc other : Doc.values()) {
      if (!matrix.expected(new Cell(cell.user(), other)).retrievable()) {
        assertThat(prompt).as("prompt holds %s", other).doesNotContain(matrix.chunkText(other));
      }
    }
  }

  @ParameterizedTest(name = "ask scoped: {0}")
  @MethodSource("cells")
  void askingScopedToTheDocumentFollowsTheMatrix(Cell cell) {
    Session session = matrix.session(cell.user());
    UUID id = matrix.id(cell.doc());

    MvcTestResult result =
        TestConversations.ask(
            mvc,
            session,
            TestConversations.create(mvc, session),
            matrix.chunkText(cell.doc()),
            List.of(id));

    Access expected = matrix.expected(cell);
    if (!expected.readable()) {
      assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
      assertThat(chat.calls()).isZero();
    } else if (expected.retrievable()) {
      assertThat(TestConversations.reply(result).outcome()).isEqualTo("ANSWERED");
      String prompt = sources(chat.requests().getFirst().user());
      assertThat(prompt).contains(matrix.chunkText(cell.doc()));
      for (Doc other : Doc.values()) {
        if (other != cell.doc()) {
          assertThat(prompt).doesNotContain(matrix.chunkText(other));
        }
      }
    } else {
      assertThat(TestConversations.reply(result).outcome()).isEqualTo("INSUFFICIENT_EVIDENCE");
      assertThat(chat.calls()).isZero();
    }
  }

  /** The sources block of a prompt: the question repeats the chunk text, so it is left out. */
  private static String sources(String prompt) {
    return prompt.substring(0, prompt.indexOf("<question>"));
  }
}
