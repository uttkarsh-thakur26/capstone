package in.edu.bennett.capstone.knowledge.conversation;

import static in.edu.bennett.capstone.knowledge.conversation.TestConversations.ask;
import static in.edu.bennett.capstone.knowledge.conversation.TestConversations.create;
import static in.edu.bennett.capstone.knowledge.conversation.TestConversations.reply;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import in.edu.bennett.capstone.knowledge.FakeChatModel;
import in.edu.bennett.capstone.knowledge.FakeEmbeddingModel;
import in.edu.bennett.capstone.knowledge.IntegrationTest;
import in.edu.bennett.capstone.knowledge.TestAccounts;
import in.edu.bennett.capstone.knowledge.TestAccounts.Account;
import in.edu.bennett.capstone.knowledge.TestAccounts.Session;
import in.edu.bennett.capstone.knowledge.ai.EmbeddingProperties;
import in.edu.bennett.capstone.knowledge.conversation.TestConversations.Reply;
import in.edu.bennett.capstone.knowledge.document.AccessMatrix;
import in.edu.bennett.capstone.knowledge.document.TestDocuments;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.client.ResourceAccessException;

/**
 * The "Conversations" tests of specification 19.2, plus the rules of Section 12 and 11.1 step 3
 * they rest on, against the scripted chat model (19.1). As in {@code QuestionAnsweringTest}, each
 * test builds its own documents from words made up for it: a question naming all six words of a
 * chunk clears the evidence gate, a short follow-up such as "and the other one?" does not, so only
 * the rewritten query can find the chunk.
 */
@IntegrationTest
class ConversationsTest {

  @Autowired MockMvcTester mvc;

  @Autowired JdbcClient jdbc;

  @Autowired FakeChatModel chat;

  @Autowired FakeEmbeddingModel embedding;

  @Autowired EmbeddingProperties embeddingProperties;

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

  /** 19.2, CONV-1: another user's conversation id returns 404 on every endpoint, admins too. */
  @Test
  void anotherUsersConversationIsNotFoundOnEveryEndpoint() {
    UUID conversation = create(mvc, student);
    reply(ask(mvc, student, conversation, "Is there a rule?", null));
    Session other = TestAccounts.login(mvc, TestAccounts.registerStudent(mvc));
    Session admin = TestAccounts.loginAsAdmin(mvc);
    String uri = "/api/v1/conversations/" + conversation;

    for (Session stranger : List.of(other, admin)) {
      assertNotFound(TestDocuments.get(mvc, stranger, uri));
      assertNotFound(send(stranger, HttpMethod.PATCH, uri, "{\"title\": \"Mine now\"}"));
      assertNotFound(send(stranger, HttpMethod.DELETE, uri, null));
      assertNotFound(ask(mvc, stranger, conversation, "Is there a rule?", null));
      assertThat(listedIds(stranger)).doesNotContain(conversation.toString());
    }
    assertThat(TestDocuments.get(mvc, student, uri)).hasStatusOk();
    assertThat(messageCount(conversation)).isEqualTo(2);
  }

  /** 19.2: deleting a conversation removes its messages and citations. */
  @Test
  void deletingAConversationRemovesItsMessagesAndCitations() {
    String[] w = words(6);
    indexed("Doc", toStudent(), String.join(" ", w));
    UUID conversation = create(mvc, student);
    Reply answered = reply(ask(mvc, student, conversation, String.join(" ", w), null));
    assertThat(answered.citations()).isNotEmpty();

    assertThat(send(student, HttpMethod.DELETE, "/api/v1/conversations/" + conversation, null))
        .hasStatus(HttpStatus.NO_CONTENT);

    assertThat(messageCount(conversation)).isZero();
    assertThat(
            jdbc.sql("SELECT count(*) FROM message_citation WHERE message_id = :id")
                .param("id", answered.messageId())
                .query(Long.class)
                .single())
        .isZero();
    assertNotFound(TestDocuments.get(mvc, student, "/api/v1/conversations/" + conversation));
  }

  /**
   * 19.2, 11.1 step 3: a follow-up is condensed into a standalone query. Retrieval searches the
   * rewritten query, while the model answers the user's own question with the history beside it;
   * the rewrite runs at temperature 0 with its own prompt and schema, and no transaction is open
   * during either call (4.5).
   */
  @Test
  void aFollowUpIsCondensedIntoAStandaloneQuery() throws Exception {
    String[] first = words(6);
    String[] second = words(6);
    indexed("First", toStudent(), String.join(" ", first));
    indexed("Second", toStudent(), String.join(" ", second));
    UUID conversation = create(mvc, student);
    String question = String.join(" ", first);
    chat.answerNext(
        "{\"answerable\": true, \"answer\": \"The first rule applies [1].\", \"citations\": [1]}");
    reply(ask(mvc, student, conversation, question, null));
    chat.reset();
    embedding.reset();

    String standalone = String.join(" ", second);
    chat.answerNext("{\"query\": \"" + standalone + "\"}");
    Reply followUp = reply(ask(mvc, student, conversation, "And the other one?", null));

    assertThat(followUp.outcome()).isEqualTo("ANSWERED");
    assertThat(chat.requests()).hasSize(2);
    FakeChatModel.Request rewrite = chat.requests().get(0);
    assertThat(rewrite.system()).isEqualTo(prompt("condense-v1"));
    assertThat(rewrite.temperature()).isEqualTo(0.0);
    assertThat(rewrite.outputSchema()).contains("\"query\"").doesNotContain("\"answerable\"");
    assertThat(rewrite.user())
        .contains("<question>\n" + question + "\n</question>", "The first rule applies.")
        .endsWith("<question>\nAnd the other one?\n</question>");
    assertThat(embedding.requests())
        .containsExactly(List.of(embeddingProperties.queryPrefix() + standalone));
    FakeChatModel.Request answer = chat.requests().get(1);
    assertThat(answer.system()).isEqualTo(prompt("answer-v2"));
    assertThat(answer.temperature()).isNull();
    assertThat(answer.user())
        .contains(standalone, "<history>", "The first rule applies.")
        .doesNotContain("The first rule applies [1]")
        .endsWith("<question>\nAnd the other one?\n</question>");
    assertThat(chat.callsInTransaction()).isZero();
    assertThat(embedding.callsInTransaction()).isZero();
  }

  /** CONV-4: only the last three usable turns go to the model. */
  @Test
  void onlyTheLastThreeTurnsAreUsed() {
    UUID conversation = create(mvc, student);
    for (int i = 1; i <= 4; i++) {
      reply(ask(mvc, student, conversation, "Question number " + i + " " + words(1)[0], null));
    }
    chat.reset();

    reply(ask(mvc, student, conversation, "And then?", null));

    String history = chat.requests().getFirst().user();
    assertThat(history)
        .doesNotContain("Question number 1")
        .contains("Question number 2", "Question number 3", "Question number 4");
  }

  /** A malformed rewrite is not trusted: the follow-up is searched as asked and still answered. */
  @Test
  void aMalformedRewriteSearchesTheQuestionAsAsked() {
    String[] w = words(6);
    indexed("Doc", toStudent(), String.join(" ", w));
    UUID conversation = create(mvc, student);
    reply(ask(mvc, student, conversation, String.join(" ", w), null));
    embedding.reset();
    String followUp = String.join(" ", w) + " again?";
    chat.answerNext("not JSON at all");

    Reply reply = reply(ask(mvc, student, conversation, followUp, null));

    assertThat(reply.outcome()).isEqualTo("ANSWERED");
    assertThat(embedding.requests())
        .containsExactly(List.of(embeddingProperties.queryPrefix() + followUp));
  }

  /** RAG-6: a provider failure during the rewrite ends the question as an error, at once. */
  @Test
  void aFailedRewriteAnswers503() {
    UUID conversation = create(mvc, student);
    reply(ask(mvc, student, conversation, "First question?", null));
    chat.failNext(() -> new ResourceAccessException("Read timed out"));

    MvcTestResult result = ask(mvc, student, conversation, "And then?", null);

    assertThat(result).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(embedding.requests()).as("only the first question was embedded").hasSize(1);
    assertThat(lastAnswerOutcome(conversation)).isEqualTo("ERROR");
  }

  /**
   * 19.2, CONV-5, CONV-6: after a grant is revoked, the earlier answer is left out of the model's
   * context, and its citation shows as no longer available, naming nothing, while the answer text
   * stays.
   */
  @Test
  void afterARevokedGrantTheEarlierAnswerLeavesTheContextAndItsCitationIsUnavailable() {
    String[] revokedWords = words(6);
    String[] keptWords = words(6);
    UUID revoked = indexed("Revoked", toStudent(), String.join(" ", revokedWords));
    indexed("Kept", toStudent(), String.join(" ", keptWords));
    UUID conversation = create(mvc, student);
    String firstQuestion = String.join(" ", revokedWords);
    Reply first = reply(ask(mvc, student, conversation, firstQuestion, null));
    assertThat(first.citations()).extracting(c -> c.documentId()).containsExactly(revoked);

    assertThat(
            send(
                owner,
                HttpMethod.PUT,
                "/api/v1/documents/" + revoked + "/grants",
                "{\"grants\": []}"))
        .hasStatusOk();
    chat.reset();
    reply(ask(mvc, student, conversation, String.join(" ", keptWords), null));

    assertThat(chat.requests()).as("no rewrite: no usable history").hasSize(1);
    assertThat(chat.requests().getFirst().user())
        .doesNotContain("<history>", firstQuestion, "The sources say so");
    assertTheFirstCitationIsUnavailable(conversation, first);
  }

  /**
   * 19.2, DATA-1, CONV-6: after a cited document is deleted, the citation keeps its snapshot in the
   * database and shows as no longer available.
   */
  @Test
  void afterADeletedDocumentTheCitationKeepsItsSnapshotAndIsUnavailable() {
    String[] w = words(6);
    String title = "Deleted " + UUID.randomUUID();
    UUID id = TestDocuments.uploaded(mvc, owner, title, toStudent());
    AccessMatrix.insertChunk(jdbc, id, 0, 2, String.join(" ", w));
    markIndexed(id);
    UUID conversation = create(mvc, student);
    Reply first = reply(ask(mvc, student, conversation, String.join(" ", w), null));

    assertThat(send(owner, HttpMethod.DELETE, "/api/v1/documents/" + id, null))
        .hasStatus(HttpStatus.NO_CONTENT);

    Map<String, Object> snapshot =
        jdbc.sql(
                "SELECT document_id, document_title, page_start, snippet FROM message_citation"
                    + " WHERE message_id = :id")
            .param("id", first.messageId())
            .query()
            .singleRow();
    assertThat(snapshot)
        .containsEntry("document_id", null)
        .containsEntry("document_title", title)
        .containsEntry("page_start", 2)
        .containsEntry("snippet", String.join(" ", w));
    assertTheFirstCitationIsUnavailable(conversation, first);
    chat.reset();
    reply(ask(mvc, student, conversation, "And then?", null));
    assertThat(chat.requests()).as("no rewrite: no usable history").isEmpty();
  }

  /**
   * ADR-038 (S19): an answer that cites an archived document stays readable with its citation, but
   * no longer reaches the model, which sees only what the user may retrieve.
   */
  @Test
  void anAnswerCitingAnArchivedDocumentLeavesTheContextButStaysAvailable() {
    String[] w = words(6);
    UUID id = indexed("Archived", toStudent(), String.join(" ", w));
    UUID conversation = create(mvc, student);
    reply(ask(mvc, student, conversation, String.join(" ", w), null));
    jdbc.sql("UPDATE document SET archived = true WHERE id = :id").param("id", id).update();
    chat.reset();

    reply(ask(mvc, student, conversation, "And then?", null));

    assertThat(chat.requests()).as("no rewrite: no usable history").isEmpty();
    String body =
        TestAccounts.body(TestDocuments.get(mvc, student, "/api/v1/conversations/" + conversation));
    assertThat((Boolean) JsonPath.read(body, "$.messages[1].citations[0].available")).isTrue();
  }

  /** An answer that failed is left out of the history (CONV-4). */
  @Test
  void aFailedAnswerIsNotHistory() {
    String[] w = words(6);
    indexed("Doc", toStudent(), String.join(" ", w));
    UUID conversation = create(mvc, student);
    String failed = String.join(" ", w);
    chat.answerNext("not JSON");
    assertThat(ask(mvc, student, conversation, failed, null))
        .hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
    chat.reset();

    reply(ask(mvc, student, conversation, "And then?", null));

    assertThat(chat.requests()).noneMatch(request -> request.user().contains(failed));
  }

  /** CONV-2: the list holds only the user's own conversations, the most recently used first. */
  @Test
  void theListHoldsOwnConversationsNewestFirst() {
    UUID older = create(mvc, student);
    UUID newer = create(mvc, student);
    reply(ask(mvc, student, older, "Asked later, so listed first?", null));
    create(mvc, owner);

    String body =
        TestAccounts.body(TestDocuments.get(mvc, student, "/api/v1/conversations?size=2"));
    assertThat((List<String>) JsonPath.read(body, "$.items[*].id"))
        .containsExactly(older.toString(), newer.toString());
    assertThat((String) JsonPath.read(body, "$.items[0].title"))
        .isEqualTo("Asked later, so listed first?");
    assertThat((Integer) JsonPath.read(body, "$.totalItems")).isEqualTo(2);
  }

  /** CONV-2: renaming; a blank or over-long title is refused. */
  @Test
  void aConversationIsRenamed() {
    UUID conversation = create(mvc, student);
    String uri = "/api/v1/conversations/" + conversation;

    assertThat(send(student, HttpMethod.PATCH, uri, "{\"title\": \"  Exam rules  \"}"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.title")
        .isEqualTo("Exam rules");
    assertThat(send(student, HttpMethod.PATCH, uri, "{\"title\": \"  \"}"))
        .hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(send(student, HttpMethod.PATCH, uri, "{\"title\": \"" + "x".repeat(201) + "\"}"))
        .hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(
            JsonPath.<String>read(
                TestAccounts.body(TestDocuments.get(mvc, student, uri)), "$.title"))
        .isEqualTo("Exam rules");
  }

  /** The conversation shows each question before its answer, with the answer's citations. */
  @Test
  void aConversationShowsItsMessagesInOrder() {
    String[] w = words(6);
    UUID id = indexed("Doc", toStudent(), String.join(" ", w));
    UUID conversation = create(mvc, student);
    Reply answered = reply(ask(mvc, student, conversation, String.join(" ", w), null));

    String body =
        TestAccounts.body(TestDocuments.get(mvc, student, "/api/v1/conversations/" + conversation));

    assertThat((List<String>) JsonPath.read(body, "$.messages[*].role"))
        .containsExactly("USER", "ASSISTANT");
    assertThat((String) JsonPath.read(body, "$.messages[1].id"))
        .isEqualTo(answered.messageId().toString());
    assertThat((String) JsonPath.read(body, "$.messages[1].outcome")).isEqualTo("ANSWERED");
    assertThat((String) JsonPath.read(body, "$.messages[1].citations[0].documentId"))
        .isEqualTo(id.toString());
    assertThat((List<?>) JsonPath.read(body, "$.messages[0].citations")).isEmpty();
  }

  private void assertTheFirstCitationIsUnavailable(UUID conversation, Reply first) {
    String body =
        TestAccounts.body(TestDocuments.get(mvc, student, "/api/v1/conversations/" + conversation));
    Map<String, Object> citation = JsonPath.read(body, "$.messages[1].citations[0]");
    assertThat(citation)
        .containsEntry("ordinal", 1)
        .containsEntry("available", false)
        .containsEntry("documentId", null)
        .containsEntry("documentTitle", null)
        .containsEntry("pageStart", null)
        .containsEntry("snippet", null);
    assertThat((String) JsonPath.read(body, "$.messages[1].content")).isEqualTo(first.answer());
  }

  private MvcTestResult send(Session session, HttpMethod method, String uri, String json) {
    return TestDocuments.send(mvc, session, method, uri, json);
  }

  private List<String> listedIds(Session session) {
    return JsonPath.read(
        TestAccounts.body(TestDocuments.get(mvc, session, "/api/v1/conversations?size=100")),
        "$.items[*].id");
  }

  private static void assertNotFound(MvcTestResult result) {
    assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
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

  private long messageCount(UUID conversation) {
    return jdbc.sql("SELECT count(*) FROM message WHERE conversation_id = :id")
        .param("id", conversation)
        .query(Long.class)
        .single();
  }

  private String lastAnswerOutcome(UUID conversation) {
    return jdbc.sql(
            "SELECT outcome FROM message WHERE conversation_id = :id AND role = 'ASSISTANT'"
                + " ORDER BY created_at DESC LIMIT 1")
        .param("id", conversation)
        .query(String.class)
        .single();
  }

  private static String prompt(String version) throws IOException {
    return new ClassPathResource("prompts/" + version + ".txt")
        .getContentAsString(StandardCharsets.UTF_8)
        .strip();
  }

  /** Words made up for one test, which no other chunk contains. */
  private static String[] words(int count) {
    String[] words = new String[count];
    for (int i = 0; i < count; i++) {
      StringBuilder word = new StringBuilder("c");
      for (char c : UUID.randomUUID().toString().replace("-", "").substring(0, 12).toCharArray()) {
        word.append((char) (Character.isDigit(c) ? 'g' + (c - '0') : c));
      }
      words[i] = word.toString();
    }
    return words;
  }
}
