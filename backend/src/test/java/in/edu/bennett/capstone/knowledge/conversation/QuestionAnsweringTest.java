package in.edu.bennett.capstone.knowledge.conversation;

import static in.edu.bennett.capstone.knowledge.conversation.TestConversations.ask;
import static in.edu.bennett.capstone.knowledge.conversation.TestConversations.create;
import static in.edu.bennett.capstone.knowledge.conversation.TestConversations.reply;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import in.edu.bennett.capstone.knowledge.AuditRows;
import in.edu.bennett.capstone.knowledge.FakeChatModel;
import in.edu.bennett.capstone.knowledge.FakeEmbeddingModel;
import in.edu.bennett.capstone.knowledge.IntegrationTest;
import in.edu.bennett.capstone.knowledge.TestAccounts;
import in.edu.bennett.capstone.knowledge.TestAccounts.Account;
import in.edu.bennett.capstone.knowledge.TestAccounts.Session;
import in.edu.bennett.capstone.knowledge.ai.EmbeddingProperties;
import in.edu.bennett.capstone.knowledge.assistant.AssistantProperties;
import in.edu.bennett.capstone.knowledge.conversation.TestConversations.Cited;
import in.edu.bennett.capstone.knowledge.conversation.TestConversations.Reply;
import in.edu.bennett.capstone.knowledge.document.AccessMatrix;
import in.edu.bennett.capstone.knowledge.document.TestDocuments;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.client.ResourceAccessException;

/**
 * The "Question answering" tests of specification 19.2, plus the rules of 11 and 12 they rest on,
 * against the scripted chat model (19.1). The test database is shared, so each test builds its own
 * documents from words made up for it. A question that names all six words of a chunk clears the
 * test floor of 0.3 (similarity about 0.5 with the query prefix); one that shares a single word
 * stays far below it (about 0.09).
 */
@IntegrationTest
class QuestionAnsweringTest {

  @Autowired MockMvcTester mvc;

  @Autowired JdbcClient jdbc;

  @Autowired FakeChatModel chat;

  @Autowired FakeEmbeddingModel embedding;

  @Autowired EmbeddingProperties embeddingProperties;

  @Autowired AssistantProperties assistant;

  private Session owner;
  private Account studentAccount;
  private Session student;

  @BeforeEach
  void accounts() {
    owner = TestAccounts.login(mvc, TestAccounts.registerAs(mvc, jdbc, "FACULTY"));
    studentAccount = TestAccounts.registerStudent(mvc);
    student = TestAccounts.login(mvc, studentAccount);
  }

  @AfterEach
  void resetTheModels() {
    chat.reset();
    embedding.reset();
  }

  /**
   * 19.2 prompt capture: the text sent to the model holds no chunk of a document the user cannot
   * read, nor of a failed or archived one, even when those match the question best (RAG-1, ACL-4).
   */
  @Test
  void onlyChunksTheUserMayRetrieveReachTheModel() {
    String[] w = words(6);
    String question = String.join(" ", w);
    String hiddenMarker = words(1)[0];
    String failedMarker = words(1)[0];
    String archivedMarker = words(1)[0];
    String visible = w[0] + " " + w[1] + " " + w[2] + " " + w[3];
    indexed("Hidden", "[]", question + " " + hiddenMarker);
    UUID failed = indexed("Failed", toStudent(), question + " " + failedMarker);
    jdbc.sql("UPDATE document SET status = 'FAILED', failure_code = 'INTERNAL' WHERE id = :id")
        .param("id", failed)
        .update();
    UUID archived = indexed("Archived", toStudent(), question + " " + archivedMarker);
    jdbc.sql("UPDATE document SET archived = true WHERE id = :id").param("id", archived).update();
    UUID shared = indexed("Shared", toStudent(), visible);

    Reply reply = TestConversations.answer(mvc, student, question);

    assertThat(reply.outcome()).isEqualTo("ANSWERED");
    assertThat(chat.requests()).hasSize(1);
    FakeChatModel.Request request = chat.requests().getFirst();
    assertThat(request.types()).containsExactly("SYSTEM", "USER");
    assertThat(request.user())
        .contains(visible)
        .doesNotContain(hiddenMarker, failedMarker, archivedMarker)
        .doesNotContain(studentAccount.email(), studentAccount.id().toString())
        .startsWith("<sources>\n")
        .endsWith("<question>\n" + question + "\n</question>");
    assertThat(request.system()).doesNotContain(hiddenMarker, studentAccount.email());
    assertThat(reply.citations()).extracting(Cited::documentId).containsOnly(shared);
  }

  /**
   * CONV-7, RET-8: a scope restricts retrieval, even when another readable document fits better.
   */
  @Test
  void aScopeKeepsOtherDocumentsOutOfThePrompt() {
    String[] w = words(6);
    String question = String.join(" ", w);
    String otherMarker = words(1)[0];
    indexed("Better", toStudent(), question + " " + otherMarker);
    UUID scoped = indexed("Scoped", toStudent(), String.join(" ", w[0], w[1], w[2], w[3], w[4]));

    Reply reply = reply(ask(mvc, student, create(mvc, student), question, List.of(scoped)));

    assertThat(reply.outcome()).isEqualTo("ANSWERED");
    assertThat(chat.requests().getFirst().user()).doesNotContain(otherMarker);
  }

  /** 19.2: below the similarity floor the outcome is insufficient evidence, with no model call. */
  @Test
  void belowTheFloorTheModelIsNeverCalled() {
    String[] w = words(11);
    UUID weak = indexed("Weak", toStudent(), String.join(" ", w[0], w[6], w[7], w[8], w[9], w[10]));
    String question = String.join(" ", w[0], w[1], w[2], w[3], w[4], w[5]);

    Reply reply = reply(ask(mvc, student, create(mvc, student), question, List.of(weak)));

    assertAbstention(reply);
    assertThat(chat.calls()).isZero();
    assertThat(storedAnswer(reply.messageId()))
        .containsEntry("outcome", "INSUFFICIENT_EVIDENCE")
        .containsEntry("model", null)
        .containsEntry("prompt_version", null);
  }

  @Test
  void withNothingRetrievableTheModelIsNeverCalled() {
    UUID empty =
        TestDocuments.uploaded(mvc, owner, "Not indexed " + UUID.randomUUID(), toStudent());

    Reply reply =
        reply(ask(mvc, student, create(mvc, student), String.join(" ", words(3)), List.of(empty)));

    assertAbstention(reply);
    assertThat(chat.calls()).isZero();
  }

  /**
   * RAG-2: the abstention never hints that restricted documents exist. A question answered only by
   * a document the student cannot read gets the same response as one answered by nothing.
   */
  @Test
  void theAbstentionIsTheSameWhetherOrNotAHiddenDocumentMatches() {
    String[] hidden = words(6);
    indexed("Restricted", "[]", String.join(" ", hidden));

    Reply aboutHidden = TestConversations.answer(mvc, student, String.join(" ", hidden));
    Reply aboutNothing = TestConversations.answer(mvc, student, String.join(" ", words(6)));

    assertAbstention(aboutHidden);
    assertAbstention(aboutNothing);
    assertThat(aboutHidden.answer()).isEqualTo(aboutNothing.answer());
    assertThat(chat.calls()).isZero();
  }

  /** 19.2: answerable=false gives insufficient evidence. */
  @Test
  void anUnanswerableModelAnswerIsInsufficientEvidence() {
    String[] w = words(6);
    indexed("Doc", toStudent(), String.join(" ", w));
    chat.answerNext("{\"answerable\": false, \"answer\": \"It is 75% [1].\", \"citations\": [1]}");

    Reply reply = TestConversations.answer(mvc, student, String.join(" ", w));

    assertAbstention(reply);
    assertThat(chat.calls()).isOne();
    assertThat(storedAnswer(reply.messageId()))
        .containsEntry("outcome", "INSUFFICIENT_EVIDENCE")
        .containsEntry("model", FakeChatModel.MODEL)
        .containsEntry("prompt_version", "answer-v2");
  }

  /** 19.2: invalid citation numbers are dropped. */
  @Test
  void invalidCitationNumbersAreDropped() {
    String[] w = words(6);
    UUID id = indexed("Doc", toStudent(), String.join(" ", w));
    chat.answerNext(
        "{\"answerable\": true, \"answer\": \"Fees are due [1]. Late fees apply [7].\","
            + " \"citations\": [1, 7, 0]}");

    Reply reply = TestConversations.answer(mvc, student, String.join(" ", w));

    assertThat(reply.outcome()).isEqualTo("ANSWERED");
    assertThat(reply.answer()).isEqualTo("Fees are due [1]. Late fees apply.");
    assertThat(reply.citations())
        .extracting(Cited::ordinal, Cited::documentId)
        .containsExactly(org.assertj.core.groups.Tuple.tuple(1, id));
    assertThat(storedCitations(reply.messageId()))
        .extracting(row -> row.get("ordinal"))
        .containsExactly(1);
  }

  /** 19.2: a factual answer with no valid citation becomes insufficient evidence. */
  @Test
  void aFactualAnswerWithoutAValidCitationIsInsufficientEvidence() {
    String[] w = words(6);
    indexed("Doc", toStudent(), String.join(" ", w));
    chat.answerNext("{\"answerable\": true, \"answer\": \"It is 75% [4].\", \"citations\": [4]}");

    Reply reply = TestConversations.answer(mvc, student, String.join(" ", w));

    assertAbstention(reply);
    assertThat(storedCitations(reply.messageId())).isEmpty();
  }

  /**
   * 19.2: citation title and pages match the database (RAG-3), and the stored snapshot holds the
   * same, with the chunk index and the start of the chunk as its snippet (DATA-1).
   */
  @Test
  void citationTitleAndPagesComeFromTheDatabase() {
    String[] w = words(6);
    String title = "Fee Rules " + UUID.randomUUID();
    UUID id = TestDocuments.uploaded(mvc, owner, title, toStudent());
    String text = String.join(" ", w);
    AccessMatrix.insertChunk(jdbc, id, 4, 3, text);
    markIndexed(id);
    chat.answerNext(
        "{\"answerable\": true, \"answer\": \"Due in July, per Some Other Title p. 99 [1].\","
            + " \"citations\": [1]}");

    Reply reply = TestConversations.answer(mvc, student, text);

    assertThat(reply.citations()).containsExactly(new Cited(1, id, title, 3, 3, text));
    assertThat(storedCitations(reply.messageId()))
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.get("document_id")).isEqualTo(id);
              assertThat(row.get("document_title")).isEqualTo(title);
              assertThat(row.get("page_start")).isEqualTo(3);
              assertThat(row.get("page_end")).isEqualTo(3);
              assertThat(row.get("chunk_index")).isEqualTo(4);
              assertThat(row.get("snippet")).isEqualTo(text);
              assertThat((Double) row.get("score")).isGreaterThan(0.3);
            });
  }

  @Test
  void aLongChunkIsStoredAsAShortSnippet() {
    String[] w = words(6);
    // Four long words make the chunk longer than the snippet while it stays close to the question.
    StringBuilder longWords = new StringBuilder();
    for (int i = 0; i < 4; i++) {
      longWords.append(' ').append(String.join("", words(5)));
    }
    String text = String.join(" ", w) + longWords;
    UUID id = TestDocuments.uploaded(mvc, owner, "Long", toStudent());
    AccessMatrix.insertChunk(jdbc, id, 0, 1, text.strip());
    markIndexed(id);

    Reply reply = TestConversations.answer(mvc, student, String.join(" ", w));

    assertThat(reply.citations().getFirst().snippet())
        .hasSizeLessThanOrEqualTo(assistant.snippetLength())
        .startsWith(String.join(" ", w))
        .endsWith("…");
  }

  /** 19.2: malformed model output is handled: an ERROR answer is stored and the client gets 503. */
  @Test
  void malformedOutputIsStoredAsAnErrorAndAnswers503() {
    String[] w = words(6);
    indexed("Doc", toStudent(), String.join(" ", w));
    chat.answerNext("The minimum attendance is 75% [1].");
    UUID conversation = create(mvc, student);

    MvcTestResult result = ask(mvc, student, conversation, String.join(" ", w), null);

    assertModelUnavailable(result);
    Map<String, Object> stored = lastAnswer(conversation);
    assertThat(stored)
        .containsEntry("outcome", "ERROR")
        .containsEntry("content", ConversationService.ERROR_TEXT)
        .containsEntry("model", FakeChatModel.MODEL)
        .containsEntry("prompt_version", "answer-v2");
    assertThat(AuditRows.forResource(jdbc, conversation.toString()))
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.get("action")).isEqualTo("AI_QUERY");
              assertThat(row.get("outcome")).isEqualTo("FAILURE");
            });
  }

  /** 19.2: a provider timeout returns 503 and stores an ERROR outcome (RAG-6). */
  @Test
  void aProviderTimeoutAnswers503AndStoresAnError() {
    String[] w = words(6);
    indexed("Doc", toStudent(), String.join(" ", w));
    chat.failNext(
        () -> new ResourceAccessException("Read timed out", new SocketTimeoutException()));
    UUID conversation = create(mvc, student);

    assertModelUnavailable(ask(mvc, student, conversation, String.join(" ", w), null));

    assertThat(lastAnswer(conversation))
        .containsEntry("outcome", "ERROR")
        .containsEntry("model", null)
        .containsEntry("prompt_version", "answer-v2");
    assertThat(questionCount(conversation)).isOne();
  }

  @Test
  void anEmbeddingFailureAnswers503AndStoresAnError() {
    embedding.failNext(() -> new TransientAiException("503"), 4);
    UUID conversation = create(mvc, student);

    assertModelUnavailable(ask(mvc, student, conversation, String.join(" ", words(3)), null));

    assertThat(lastAnswer(conversation))
        .containsEntry("outcome", "ERROR")
        .containsEntry("model", null)
        .containsEntry("prompt_version", null);
    assertThat(chat.calls()).isZero();
  }

  /**
   * 11.1 step 9, 4.5: the question, the answer and the citations are stored; no transaction is open
   * while a model works; the question is embedded with the query prefix; the audit entry holds no
   * question or answer text (AUD-2).
   */
  @Test
  void theExchangeIsStoredWithoutATransactionAroundTheModels() {
    String[] w = words(6);
    UUID id = indexed("Doc", toStudent(), String.join(" ", w));
    String question = "  " + String.join(" ", w) + "  ";
    UUID conversation = create(mvc, student);

    Reply reply = reply(ask(mvc, student, conversation, question, null));

    assertThat(chat.callsInTransaction()).isZero();
    assertThat(embedding.callsInTransaction()).isZero();
    assertThat(embedding.requests())
        .containsExactly(List.of(embeddingProperties.queryPrefix() + question.strip()));

    List<Map<String, Object>> rows =
        jdbc.sql(
                """
                SELECT id, role, content, outcome, model, prompt_version, created_at
                FROM message WHERE conversation_id = :id ORDER BY created_at
                """)
            .param("id", conversation)
            .query()
            .listOfRows();
    assertThat(rows).hasSize(2);
    assertThat(rows.get(0))
        .containsEntry("role", "USER")
        .containsEntry("content", question.strip())
        .containsEntry("outcome", null);
    assertThat(rows.get(1))
        .containsEntry("id", reply.messageId())
        .containsEntry("role", "ASSISTANT")
        .containsEntry("content", reply.answer())
        .containsEntry("outcome", "ANSWERED")
        .containsEntry("model", FakeChatModel.MODEL)
        .containsEntry("prompt_version", "answer-v2");
    assertThat(storedCitations(reply.messageId()))
        .extracting(row -> row.get("document_id"))
        .containsExactly(id);
    assertThat(conversationTitle(conversation)).isEqualTo(Conversation.titleOf(question));

    assertThat(AuditRows.forResource(jdbc, conversation.toString()))
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.get("action")).isEqualTo("AI_QUERY");
              assertThat(row.get("outcome")).isEqualTo("SUCCESS");
              assertThat(row.get("resource_type")).isEqualTo("CONVERSATION");
              assertThat(row.get("actor_user_id")).isEqualTo(studentAccount.id().toString());
              assertThat((String) row.get("details"))
                  .contains(reply.messageId().toString(), "ANSWERED")
                  .doesNotContain(w[0], "The sources say so");
            });
  }

  /** CONV-3: the first question names the conversation; later ones do not rename it. */
  @Test
  void theFirstQuestionNamesTheConversation() {
    UUID conversation = create(mvc, student);
    String first = "Is there a rule about " + "x".repeat(100);

    reply(ask(mvc, student, conversation, first, null));
    reply(ask(mvc, student, conversation, "Another question?", null));

    assertThat(conversationTitle(conversation))
        .hasSize(Conversation.TITLE_LENGTH)
        .startsWith("Is there a rule about xxx")
        .endsWith("…");
    assertThat(questionCount(conversation)).isEqualTo(2);
  }

  /** CONV-1: another user's conversation answers 404, and nothing is asked or stored. */
  @Test
  void anotherUsersConversationIsNotFound() {
    UUID owners = create(mvc, owner);

    assertNotFound(ask(mvc, student, owners, "What is the fee?", null));
    assertNotFound(ask(mvc, student, UUID.randomUUID(), "What is the fee?", null));

    assertThat(questionCount(owners)).isZero();
    assertThat(embedding.requests()).isEmpty();
    assertThat(chat.calls()).isZero();
  }

  /** RET-8: an unreadable id in the scope answers 404, as a missing one does. */
  @Test
  void anUnreadableScopeIdIsNotFound() {
    UUID hidden = indexed("Hidden", "[]", String.join(" ", words(6)));
    UUID conversation = create(mvc, student);

    assertNotFound(ask(mvc, student, conversation, "What is the fee?", List.of(hidden)));
    assertNotFound(ask(mvc, student, conversation, "What is the fee?", List.of(UUID.randomUUID())));

    assertThat(questionCount(conversation)).isZero();
    assertThat(chat.calls()).isZero();
  }

  @Test
  void theQuestionIsValidated() {
    UUID conversation = create(mvc, student);

    assertThat(ask(mvc, student, conversation, "   ", null)).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(ask(mvc, student, conversation, "x".repeat(1001), null))
        .hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(ask(mvc, student, conversation, "bell\u0007", null))
        .hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(
            mvc.post()
                .uri("/api/v1/conversations/" + conversation + "/messages")
                .contentType("application/json")
                .content("{\"question\": \"Fee?\"}"))
        .hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(questionCount(conversation)).isZero();
  }

  private static void assertAbstention(Reply reply) {
    assertThat(reply.outcome()).isEqualTo("INSUFFICIENT_EVIDENCE");
    assertThat(reply.answer())
        .isEqualTo(
            "Insufficient information was found in the available authorized documents to answer"
                + " this question.");
    assertThat(reply.citations()).isEmpty();
  }

  private static void assertModelUnavailable(MvcTestResult result) {
    assertThat(result).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat((String) JsonPath.read(TestAccounts.body(result), "$.code"))
        .isEqualTo("MODEL_UNAVAILABLE");
  }

  private static void assertNotFound(MvcTestResult result) {
    assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    assertThat((String) JsonPath.read(TestAccounts.body(result), "$.code")).isEqualTo("NOT_FOUND");
  }

  private UUID indexed(String title, String grantsJson, String chunk) {
    UUID id = TestDocuments.uploaded(mvc, owner, title + " " + UUID.randomUUID(), grantsJson);
    AccessMatrix.insertChunk(jdbc, id, 0, 1, chunk);
    markIndexed(id);
    return id;
  }

  private void markIndexed(UUID id) {
    jdbc.sql("UPDATE document SET status = 'INDEXED', indexed_at = now() WHERE id = :id")
        .param("id", id)
        .update();
  }

  private String toStudent() {
    return "[{\"type\": \"USER\", \"userId\": \"%s\"}]".formatted(studentAccount.id());
  }

  private Map<String, Object> storedAnswer(UUID messageId) {
    return jdbc.sql("SELECT outcome, model, prompt_version FROM message WHERE id = :id")
        .param("id", messageId)
        .query()
        .singleRow();
  }

  private Map<String, Object> lastAnswer(UUID conversation) {
    return jdbc.sql(
            """
            SELECT content, outcome, model, prompt_version FROM message
            WHERE conversation_id = :id AND role = 'ASSISTANT'
            ORDER BY created_at DESC LIMIT 1
            """)
        .param("id", conversation)
        .query()
        .singleRow();
  }

  private List<Map<String, Object>> storedCitations(UUID messageId) {
    return jdbc.sql(
            """
            SELECT ordinal, document_id, document_title, page_start, page_end, chunk_index, snippet,
                   score
            FROM message_citation WHERE message_id = :id ORDER BY ordinal
            """)
        .param("id", messageId)
        .query()
        .listOfRows();
  }

  private long questionCount(UUID conversation) {
    return jdbc.sql("SELECT count(*) FROM message WHERE conversation_id = :id AND role = 'USER'")
        .param("id", conversation)
        .query(Long.class)
        .single();
  }

  private String conversationTitle(UUID conversation) {
    return jdbc.sql("SELECT title FROM conversation WHERE id = :id")
        .param("id", conversation)
        .query(String.class)
        .single();
  }

  /** Words made up for one test, which no other chunk contains. */
  private static String[] words(int count) {
    String[] words = new String[count];
    for (int i = 0; i < count; i++) {
      StringBuilder word = new StringBuilder("q");
      for (char c : UUID.randomUUID().toString().replace("-", "").substring(0, 12).toCharArray()) {
        word.append((char) (Character.isDigit(c) ? 'g' + (c - '0') : c));
      }
      words[i] = word.toString();
    }
    return words;
  }
}
