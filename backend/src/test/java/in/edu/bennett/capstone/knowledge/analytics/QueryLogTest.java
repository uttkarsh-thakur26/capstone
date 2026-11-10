package in.edu.bennett.capstone.knowledge.analytics;

import static in.edu.bennett.capstone.knowledge.assistant.TestSummaries.awaitFinished;
import static in.edu.bennett.capstone.knowledge.assistant.TestSummaries.compare;
import static in.edu.bennett.capstone.knowledge.assistant.TestSummaries.indexed;
import static in.edu.bennett.capstone.knowledge.assistant.TestSummaries.request;
import static org.assertj.core.api.Assertions.assertThat;

import in.edu.bennett.capstone.knowledge.FakeChatModel;
import in.edu.bennett.capstone.knowledge.IntegrationTest;
import in.edu.bennett.capstone.knowledge.TestAccounts;
import in.edu.bennett.capstone.knowledge.TestAccounts.Account;
import in.edu.bennett.capstone.knowledge.TestAccounts.Session;
import in.edu.bennett.capstone.knowledge.conversation.TestConversations;
import in.edu.bennett.capstone.knowledge.retrieval.SearchMode;
import in.edu.bennett.capstone.knowledge.retrieval.TestSearch;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.client.ResourceAccessException;

/**
 * The query log (specification 7.2, 14.3, ADR-041): one row per search, question, summary and
 * comparison through the real endpoints, with the outcome, times, tokens and cost, and no column
 * that could hold the text of a question (D14).
 */
@IntegrationTest
class QueryLogTest {

  private static final String RULE = "Hostel gates close at 22:30 for every resident student.";
  private static final String QUESTION = "When do hostel gates close for every resident student?";

  /** The fake chat model reports 1,200 input and 80 output tokens; at $0.10 and $0.50 a million. */
  private static final BigDecimal FAKE_CALL_COST = new BigDecimal("0.000160");

  @Autowired MockMvcTester mvc;

  @Autowired JdbcClient jdbc;

  @Autowired FakeChatModel chat;

  private Account account;
  private Session faculty;

  @BeforeEach
  void signIn() {
    account = TestAccounts.registerAs(mvc, jdbc, "FACULTY");
    faculty = TestAccounts.login(mvc, account);
  }

  @BeforeEach
  @AfterEach
  void resetTheModel() {
    chat.reset();
  }

  @Test
  void anAnsweredQuestionLogsItsTimesTokensAndCost() {
    indexed(mvc, jdbc, faculty, "Hostel rules " + UUID.randomUUID(), "[]", 20, RULE);

    assertThat(TestConversations.answer(mvc, faculty, QUESTION).outcome()).isEqualTo("ANSWERED");

    Map<String, Object> row = single("ASK");
    assertThat(row.get("outcome")).isEqualTo("ANSWERED");
    assertThat(row.get("model")).isEqualTo(FakeChatModel.MODEL);
    assertThat(row.get("model_calls")).isEqualTo(1);
    assertThat(row.get("input_tokens")).isEqualTo(1200);
    assertThat(row.get("output_tokens")).isEqualTo(80);
    assertThat((BigDecimal) row.get("cost_usd")).isEqualByComparingTo(FAKE_CALL_COST);
    assertThat((Integer) row.get("retrieved_count")).isPositive();
    assertThat((Double) row.get("top_similarity")).isGreaterThan(0.3);
    assertThat(row.get("retrieval_ms")).isNotNull();
    assertThat(row.get("generation_ms")).isNotNull();
    assertThat((Integer) row.get("total_ms"))
        .isGreaterThanOrEqualTo((Integer) row.get("generation_ms"));
  }

  @Test
  void aQuestionStoppedByTheEvidenceGateCostsNothing() {
    indexed(mvc, jdbc, faculty, "Hostel rules " + UUID.randomUUID(), "[]", 20, RULE);

    assertThat(TestConversations.answer(mvc, faculty, "canteen menu fridays").outcome())
        .isEqualTo("INSUFFICIENT_EVIDENCE");

    Map<String, Object> row = single("ASK");
    assertThat(row.get("outcome")).isEqualTo("INSUFFICIENT_EVIDENCE");
    assertThat(row.get("model")).isNull();
    assertThat(row.get("model_calls")).isEqualTo(0);
    assertThat(row.get("generation_ms")).isNull();
    assertThat((BigDecimal) row.get("cost_usd")).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(chat.calls()).isZero();
  }

  @Test
  void aFailedAnswerIsLoggedAsAnError() {
    indexed(mvc, jdbc, faculty, "Hostel rules " + UUID.randomUUID(), "[]", 20, RULE);
    chat.failNext(
        () -> new ResourceAccessException("Read timed out", new SocketTimeoutException()));

    assertThat(
            TestConversations.ask(
                mvc, faculty, TestConversations.create(mvc, faculty), QUESTION, null))
        .hasStatus(HttpStatus.SERVICE_UNAVAILABLE);

    assertThat(single("ASK").get("outcome")).isEqualTo("ERROR");
  }

  @Test
  void eachSearchModeIsLoggedWithItsBestSimilarity() {
    indexed(mvc, jdbc, faculty, "Hostel rules " + UUID.randomUUID(), "[]", 20, RULE);

    for (SearchMode mode : SearchMode.values()) {
      assertThat(TestSearch.hits(mvc, faculty, mode, "hostel gates resident", null)).isNotEmpty();
    }

    Map<String, Object> keyword = single("SEARCH_KEYWORD");
    assertThat(keyword.get("outcome")).isEqualTo("OK");
    assertThat(keyword.get("top_similarity")).isNull();
    assertThat((Integer) keyword.get("retrieved_count")).isPositive();
    for (String type : List.of("SEARCH_SEMANTIC", "SEARCH_HYBRID")) {
      Map<String, Object> row = single(type);
      assertThat(row.get("outcome")).isEqualTo("OK");
      assertThat((Double) row.get("top_similarity")).isPositive();
      assertThat(row.get("model_calls")).isEqualTo(0);
      assertThat((BigDecimal) row.get("cost_usd")).isEqualByComparingTo(BigDecimal.ZERO);
    }
  }

  @Test
  void aSummaryRunIsLoggedWithItsCallsAndARepeatedRequestAsReused() {
    UUID id =
        indexed(mvc, jdbc, faculty, "Hostel rules " + UUID.randomUUID(), "[]", 20, RULE, RULE);

    assertThat(request(mvc, faculty, id)).hasStatus(HttpStatus.ACCEPTED);
    awaitFinished(mvc, faculty, id);
    Map<String, Object> run = awaitSingle("SUMMARIZE");
    assertThat(run.get("outcome")).isEqualTo("OK");
    assertThat(run.get("model_calls")).isEqualTo(1);
    assertThat((BigDecimal) run.get("cost_usd")).isEqualByComparingTo(FAKE_CALL_COST);
    assertThat(run.get("generation_ms")).isNotNull();

    assertThat(request(mvc, faculty, id)).hasStatusOk();
    List<Map<String, Object>> rows = rows("SUMMARIZE");
    assertThat(rows).hasSize(2);
    assertThat(rows.get(1).get("outcome")).isEqualTo("REUSED");
    assertThat(rows.get(1).get("model_calls")).isEqualTo(0);
    assertThat((BigDecimal) rows.get(1).get("cost_usd")).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void aComparisonIsLoggedWithItsCost() {
    UUID left = indexed(mvc, jdbc, faculty, "Rules A " + UUID.randomUUID(), "[]", 20, RULE);
    UUID right =
        indexed(
            mvc,
            jdbc,
            faculty,
            "Rules B " + UUID.randomUUID(),
            "[]",
            20,
            "Hostel gates close at 23:00 for every resident student.");

    assertThat(compare(mvc, faculty, left, right)).hasStatusOk();

    Map<String, Object> row = single("COMPARE");
    assertThat(row.get("outcome")).isEqualTo("OK");
    assertThat(row.get("model_calls")).isEqualTo(1);
    assertThat((BigDecimal) row.get("cost_usd")).isEqualByComparingTo(FAKE_CALL_COST);
  }

  /** D14: the only text columns are the type, the outcome and the model's name. */
  @Test
  void noColumnCanHoldTheTextOfAQuestion() {
    List<String> textColumns =
        jdbc.sql(
                """
                SELECT column_name FROM information_schema.columns
                WHERE table_name = 'query_log' AND data_type IN ('text', 'character varying', 'jsonb')
                ORDER BY column_name
                """)
            .query(String.class)
            .list();

    assertThat(textColumns).containsExactly("model", "outcome", "type");
  }

  private Map<String, Object> single(String type) {
    List<Map<String, Object>> rows = rows(type);
    assertThat(rows).as("query_log rows of type %s", type).hasSize(1);
    return rows.getFirst();
  }

  /** The summary's row is written on the summary pool, just after the summary is stored. */
  private Map<String, Object> awaitSingle(String type) {
    long deadline = System.nanoTime() + 5_000_000_000L;
    while (rows(type).isEmpty() && System.nanoTime() < deadline) {
      try {
        Thread.sleep(20);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError(e);
      }
    }
    return single(type);
  }

  private List<Map<String, Object>> rows(String type) {
    return jdbc.sql("SELECT * FROM query_log WHERE user_id = :user AND type = :type ORDER BY id")
        .param("user", account.id())
        .param("type", type)
        .query()
        .listOfRows();
  }
}
