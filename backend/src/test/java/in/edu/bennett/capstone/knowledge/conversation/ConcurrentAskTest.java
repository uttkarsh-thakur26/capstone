package in.edu.bennett.capstone.knowledge.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import in.edu.bennett.capstone.knowledge.FakeChatModel;
import in.edu.bennett.capstone.knowledge.IntegrationTest;
import in.edu.bennett.capstone.knowledge.TestAccounts;
import in.edu.bennett.capstone.knowledge.TestAccounts.Account;
import in.edu.bennett.capstone.knowledge.TestAccounts.Session;
import in.edu.bennett.capstone.knowledge.document.AccessMatrix;
import in.edu.bennett.capstone.knowledge.document.TestDocuments;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Asking while the model works: what happens to a conversation when a second question, or its
 * deletion, arrives during the model call (4.5 keeps no lock or transaction across it).
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class ConcurrentAskTest {

  @Autowired MockMvcTester mvc;

  @Autowired JdbcClient jdbc;

  @Autowired FakeChatModel chat;

  private Session owner;
  private Account studentAccount;
  private Session student;
  private String chunk;
  private final CountDownLatch release = new CountDownLatch(1);

  @BeforeEach
  void aDocumentToAskAbout() {
    owner = TestAccounts.login(mvc, TestAccounts.registerAs(mvc, jdbc, "FACULTY"));
    studentAccount = TestAccounts.registerStudent(mvc);
    student = TestAccounts.login(mvc, studentAccount);
    chunk = "concurrent " + UUID.randomUUID().toString().replace("-", " ");
    UUID id =
        TestDocuments.uploaded(
            mvc,
            owner,
            "Concurrent " + UUID.randomUUID(),
            "[{\"type\": \"USER\", \"userId\": \"%s\"}]".formatted(studentAccount.id()));
    AccessMatrix.insertChunk(jdbc, id, 0, 1, chunk);
    jdbc.sql("UPDATE document SET status = 'INDEXED', indexed_at = now() WHERE id = :id")
        .param("id", id)
        .update();
  }

  @AfterEach
  void resetTheChatModel() {
    release.countDown();
    chat.reset();
  }

  /**
   * A question asked second but answered first is stored first, and each question stays next to its
   * own answer, so the order of a conversation's messages always pairs them (history, CONV-4).
   */
  @Test
  void eachQuestionStaysNextToItsAnswer() throws Exception {
    UUID conversation = TestConversations.create(mvc, student);
    CountDownLatch slowStarted = chat.holdNext(release, () -> FakeChatModel.DEFAULT_ANSWER);
    CompletableFuture<MvcTestResult> slow =
        CompletableFuture.supplyAsync(
            () -> TestConversations.ask(mvc, student, conversation, "slow " + chunk, null));
    assertThat(slowStarted.await(10, TimeUnit.SECONDS)).isTrue();

    TestConversations.reply(
        TestConversations.ask(mvc, student, conversation, "fast " + chunk, null));
    release.countDown();
    TestConversations.reply(slow.get(10, TimeUnit.SECONDS));

    List<Map<String, Object>> rows =
        jdbc.sql(
                "SELECT role, content FROM message WHERE conversation_id = :id"
                    + " ORDER BY created_at, id")
            .param("id", conversation)
            .query()
            .listOfRows();
    assertThat(rows)
        .extracting(row -> row.get("role"))
        .containsExactly("USER", "ASSISTANT", "USER", "ASSISTANT");
    assertThat(rows.get(0).get("content")).isEqualTo("fast " + chunk);
    assertThat(rows.get(2).get("content")).isEqualTo("slow " + chunk);
  }

  /**
   * A conversation deleted while the model fails cannot keep the failed answer: the client gets 404
   * and nothing is stored, but the failure itself is still logged.
   */
  @Test
  void aFailureInADeletedConversationIsStillLogged(CapturedOutput output) throws Exception {
    UUID conversation = TestConversations.create(mvc, student);
    CountDownLatch started = chat.holdNext(release, () -> "not JSON");
    CompletableFuture<MvcTestResult> asked =
        CompletableFuture.supplyAsync(
            () -> TestConversations.ask(mvc, student, conversation, chunk, null));
    assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();

    jdbc.sql("DELETE FROM conversation WHERE id = :id").param("id", conversation).update();
    release.countDown();

    assertThat(asked.get(10, TimeUnit.SECONDS)).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(
            jdbc.sql("SELECT count(*) FROM message WHERE conversation_id = :id")
                .param("id", conversation)
                .query(Long.class)
                .single())
        .isZero();
    assertThat(output.getAll())
        .contains(
            "conversation=" + conversation + " message=null outcome=ERROR",
            "failure=\"The chat model's output is malformed\"");
  }
}
