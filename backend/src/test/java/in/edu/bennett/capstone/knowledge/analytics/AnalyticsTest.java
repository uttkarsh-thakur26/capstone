package in.edu.bennett.capstone.knowledge.analytics;

import static in.edu.bennett.capstone.knowledge.assistant.TestSummaries.awaitFinished;
import static in.edu.bennett.capstone.knowledge.assistant.TestSummaries.compare;
import static in.edu.bennett.capstone.knowledge.assistant.TestSummaries.indexed;
import static in.edu.bennett.capstone.knowledge.assistant.TestSummaries.request;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import in.edu.bennett.capstone.knowledge.FakeChatModel;
import in.edu.bennett.capstone.knowledge.IntegrationTest;
import in.edu.bennett.capstone.knowledge.TestAccounts;
import in.edu.bennett.capstone.knowledge.TestAccounts.Account;
import in.edu.bennett.capstone.knowledge.TestAccounts.Session;
import in.edu.bennett.capstone.knowledge.conversation.TestConversations;
import in.edu.bennett.capstone.knowledge.document.TestDocuments;
import in.edu.bennett.capstone.knowledge.retrieval.SearchMode;
import in.edu.bennett.capstone.knowledge.retrieval.TestSearch;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Phase 9 exit criterion: the analytics figures (14.3) match direct database queries. The test
 * makes known activity through the real endpoints, then compares every figure of {@code GET
 * /admin/analytics} with a statement of its own, written independently of the service's, over the
 * same period in the same zone. The test's own rows are also counted exactly.
 */
@IntegrationTest
class AnalyticsTest {

  private static final String ZONE = "Asia/Kolkata";
  private static final int DAYS = 7;
  private static final String RULE = "Hostel gates close at 22:30 for every resident student.";

  /** The period, as SQL of its own: the last seven days in the zone, today included. */
  private static final String PERIOD =
      """
      WITH p AS (
        SELECT (date_trunc('day', now() AT TIME ZONE :zone) - interval '6 days') AT TIME ZONE :zone
                   AS f,
               (date_trunc('day', now() AT TIME ZONE :zone) + interval '1 day') AT TIME ZONE :zone
                   AS t)
      """;

  @Autowired MockMvcTester mvc;

  @Autowired JdbcClient jdbc;

  @Autowired FakeChatModel chat;

  private Session admin;
  private Account account;
  private Session faculty;

  @BeforeEach
  void setUp() {
    chat.reset();
    admin = TestAccounts.loginAsAdmin(mvc);
    account = TestAccounts.registerAs(mvc, jdbc, "FACULTY");
    faculty = TestAccounts.login(mvc, account);
  }

  @AfterEach
  void resetTheModel() {
    chat.reset();
  }

  @Test
  void everyFigureMatchesADirectQuery() {
    makeActivity();

    String body = analytics(DAYS, ZONE);

    // The test's own rows, counted exactly.
    Map<String, Object> mine =
        jdbc.sql(
                """
                SELECT count(*) FILTER (WHERE type = 'ASK' AND outcome = 'ANSWERED') AS answered,
                       count(*) FILTER (WHERE type = 'ASK' AND outcome = 'INSUFFICIENT_EVIDENCE')
                           AS no_answer,
                       count(*) FILTER (WHERE type = 'SEARCH_KEYWORD') AS keyword,
                       count(*) FILTER (WHERE type = 'SUMMARIZE' AND outcome = 'OK') AS summaries,
                       count(*) FILTER (WHERE type = 'COMPARE' AND outcome = 'OK') AS comparisons,
                       sum(cost_usd) AS cost
                FROM query_log WHERE user_id = :user
                """)
            .param("user", account.id())
            .query()
            .singleRow();
    assertThat(mine.get("answered")).isEqualTo(2L);
    assertThat(mine.get("no_answer")).isEqualTo(1L);
    assertThat(mine.get("keyword")).isEqualTo(1L);
    assertThat(mine.get("summaries")).isEqualTo(1L);
    assertThat(mine.get("comparisons")).isEqualTo(1L);
    // Two answers, one summary call and one comparison call, each 1,200 in and 80 out.
    assertThat((BigDecimal) mine.get("cost")).isEqualByComparingTo("0.000640");

    // The period.
    LocalDate today = LocalDate.now(ZoneId.of(ZONE));
    assertThat(read(body, "$.period.from")).isEqualTo(today.minusDays(DAYS - 1).toString());
    assertThat(read(body, "$.period.to")).isEqualTo(today.toString());
    assertThat((List<?>) JsonPath.read(body, "$.questionsPerDay")).hasSize(DAYS);
    assertThat((List<?>) JsonPath.read(body, "$.costPerDay")).hasSize(DAYS);

    // Totals against query_log.
    Map<String, Object> totals =
        direct(
            """
            SELECT count(*) FILTER (WHERE type = 'ASK') AS questions,
                   count(*) FILTER (WHERE type = 'ASK' AND outcome = 'ANSWERED') AS answered,
                   count(*) FILTER (WHERE type = 'ASK' AND outcome = 'INSUFFICIENT_EVIDENCE')
                       AS no_answer,
                   count(*) FILTER (WHERE type = 'ASK' AND outcome = 'ERROR') AS failed,
                   count(*) FILTER (WHERE type LIKE 'SEARCH%') AS searches,
                   count(*) FILTER (WHERE type = 'SUMMARIZE') AS summaries,
                   count(*) FILTER (WHERE type = 'COMPARE') AS comparisons,
                   coalesce(sum(model_calls), 0) AS calls,
                   coalesce(sum(input_tokens), 0) AS input,
                   coalesce(sum(cached_input_tokens), 0) AS cached,
                   coalesce(sum(output_tokens), 0) AS output,
                   coalesce(sum(cost_usd), 0) AS cost
            FROM query_log, p WHERE created_at >= p.f AND created_at < p.t
            """);
    assertThat(number(body, "$.totals.questions")).isEqualTo(asLong(totals.get("questions")));
    assertThat(number(body, "$.totals.answered")).isEqualTo(asLong(totals.get("answered")));
    assertThat(number(body, "$.totals.noAnswer")).isEqualTo(asLong(totals.get("no_answer")));
    assertThat(number(body, "$.totals.failedQuestions")).isEqualTo(asLong(totals.get("failed")));
    assertThat(number(body, "$.totals.searches")).isEqualTo(asLong(totals.get("searches")));
    assertThat(number(body, "$.totals.summaries")).isEqualTo(asLong(totals.get("summaries")));
    assertThat(number(body, "$.totals.comparisons")).isEqualTo(asLong(totals.get("comparisons")));
    assertThat(number(body, "$.totals.modelCalls")).isEqualTo(asLong(totals.get("calls")));
    assertThat(number(body, "$.totals.inputTokens")).isEqualTo(asLong(totals.get("input")));
    assertThat(number(body, "$.totals.cachedInputTokens")).isEqualTo(asLong(totals.get("cached")));
    assertThat(number(body, "$.totals.outputTokens")).isEqualTo(asLong(totals.get("output")));
    assertThat(new BigDecimal(JsonPath.read(body, "$.totals.costUsd").toString()))
        .isEqualByComparingTo((BigDecimal) totals.get("cost"));
    long answered = (Long) totals.get("answered");
    long noAnswer = (Long) totals.get("no_answer");
    assertThat((Double) JsonPath.read(body, "$.totals.insufficientEvidenceRate"))
        .isCloseTo((double) noAnswer / (answered + noAnswer), within());

    // Today's rows against a per-day statement.
    Map<String, Object> todays =
        direct(
            """
            SELECT count(*) FILTER (WHERE type = 'ASK' AND outcome = 'ANSWERED') AS answered,
                   count(*) FILTER (WHERE type = 'ASK' AND outcome = 'INSUFFICIENT_EVIDENCE')
                       AS no_answer,
                   count(*) FILTER (WHERE type = 'SEARCH_KEYWORD') AS keyword,
                   count(*) FILTER (WHERE type = 'COMPARE') AS comparisons,
                   coalesce(sum(cost_usd), 0) AS cost
            FROM query_log, p
            WHERE (created_at AT TIME ZONE :zone)::date = (now() AT TIME ZONE :zone)::date
            """);
    int last = DAYS - 1;
    assertThat(read(body, "$.questionsPerDay[" + last + "].date")).isEqualTo(today.toString());
    assertThat(number(body, "$.questionsPerDay[" + last + "].answered"))
        .isEqualTo(asLong(todays.get("answered")));
    assertThat(number(body, "$.questionsPerDay[" + last + "].noAnswer"))
        .isEqualTo(asLong(todays.get("no_answer")));
    assertThat(number(body, "$.queriesPerDay[" + last + "].keywordSearches"))
        .isEqualTo(asLong(todays.get("keyword")));
    assertThat(number(body, "$.queriesPerDay[" + last + "].comparisons"))
        .isEqualTo(asLong(todays.get("comparisons")));
    assertThat(new BigDecimal(JsonPath.read(body, "$.costPerDay[" + last + "].costUsd").toString()))
        .isEqualByComparingTo((BigDecimal) todays.get("cost"));

    // Uploads from the audit log.
    long uploads =
        (Long)
            direct(
                    """
                    SELECT count(*) AS n FROM audit_log, p
                    WHERE action = 'DOCUMENT_UPLOADED' AND outcome = 'SUCCESS'
                      AND occurred_at >= p.f AND occurred_at < p.t
                    """)
                .get("n");
    List<Integer> perDay = JsonPath.read(body, "$.uploadsPerDay[*].count");
    assertThat(perDay.stream().mapToLong(Integer::longValue).sum()).isEqualTo(uploads);

    // Latency of questions.
    Map<String, Object> latency =
        direct(
            """
            SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY total_ms) AS p50,
                   percentile_cont(0.95) WITHIN GROUP (ORDER BY generation_ms) AS g95
            FROM query_log, p WHERE type = 'ASK' AND outcome NOT IN ('REUSED', 'ERROR')
              AND created_at >= p.f AND created_at < p.t
            """);
    List<Map<String, Object>> ask = JsonPath.read(body, "$.latency[?(@.type == 'ASK')]");
    assertThat(((Number) ask.getFirst().get("totalP50")).doubleValue())
        .isCloseTo((Double) latency.get("p50"), within());
    assertThat(((Number) ask.getFirst().get("generationP95")).doubleValue())
        .isCloseTo((Double) latency.get("g95"), within());

    // Latency describes requests that ran: a reused summary or a failed request is left out.
    long summaryRuns =
        asLong(
            direct(
                    """
                    SELECT count(*) AS n FROM query_log, p
                    WHERE type = 'SUMMARIZE' AND outcome NOT IN ('REUSED', 'ERROR')
                      AND created_at >= p.f AND created_at < p.t
                    """)
                .get("n"));
    List<Map<String, Object>> summarize =
        JsonPath.read(body, "$.latency[?(@.type == 'SUMMARIZE')]");
    assertThat(asLong(summarize.getFirst().get("count"))).isEqualTo(summaryRuns);

    // Current state: documents, failures, users, chunks.
    assertThat(counts(body, "$.documentsByStatus"))
        .containsAllEntriesOf(
            grouped("SELECT status AS name, count(*) AS n FROM document GROUP BY status"));
    assertThat(counts(body, "$.failuresByCode"))
        .isEqualTo(
            grouped(
                "SELECT failure_code AS name, count(*) AS n FROM document"
                    + " WHERE status = 'FAILED' GROUP BY failure_code"));
    assertThat(counts(body, "$.usersByRole"))
        .containsAllEntriesOf(
            grouped("SELECT role AS name, count(*) AS n FROM app_user GROUP BY role"));
    assertThat(number(body, "$.system.documents"))
        .isEqualTo(jdbc.sql("SELECT count(*) FROM document").query(Long.class).single());
    assertThat(number(body, "$.system.chunks"))
        .isEqualTo(jdbc.sql("SELECT count(*) FROM document_chunk").query(Long.class).single());
    assertThat(read(body, "$.system.chatModel")).isEqualTo(FakeChatModel.MODEL);

    // Most cited documents in the period.
    List<Map<String, Object>> cited =
        jdbc.sql(
                PERIOD
                    + """
                    SELECT c.document_id::text AS id, count(*) AS n
                    FROM message_citation c, message m, p
                    WHERE m.id = c.message_id AND c.document_id IS NOT NULL
                      AND m.created_at >= p.f AND m.created_at < p.t
                    GROUP BY c.document_id ORDER BY n DESC LIMIT 5
                    """)
            .param("zone", ZONE)
            .query()
            .listOfRows();
    List<Integer> citations = JsonPath.read(body, "$.mostCitedDocuments[*].citations");
    assertThat(citations.stream().map(Integer::longValue).toList())
        .isEqualTo(cited.stream().map(row -> (Long) row.get("n")).toList());
  }

  /** Chromium on Windows reports the legacy Asia/Calcutta, which the database image lacks. */
  @Test
  void aLegacyZoneNameIsReadAsTheDatabaseKnowsIt() {
    String body = analytics(DAYS, "Asia/Calcutta");

    assertThat(read(body, "$.period.zone")).isEqualTo("Asia/Kolkata");
  }

  @Test
  void aZoneNameIsRequiredAndThePeriodIsBounded() {
    assertThat(TestDocuments.get(mvc, admin, "/api/v1/admin/analytics?zone=%2B05:30"))
        .hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(TestDocuments.get(mvc, admin, "/api/v1/admin/analytics?zone=Mars/Olympus"))
        .hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(TestDocuments.get(mvc, admin, "/api/v1/admin/analytics?days=0"))
        .hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(TestDocuments.get(mvc, admin, "/api/v1/admin/analytics?days=367"))
        .hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(TestDocuments.get(mvc, admin, "/api/v1/admin/analytics")).hasStatusOk();
  }

  /** Two answers, one "No answer found", one keyword search, a summary and a comparison. */
  private void makeActivity() {
    UUID left = indexed(mvc, jdbc, faculty, "Rules " + UUID.randomUUID(), "[]", 20, RULE);
    UUID right =
        indexed(
            mvc,
            jdbc,
            faculty,
            "Rules " + UUID.randomUUID(),
            "[]",
            20,
            "Hostel gates close at 23:00 for every resident student.");
    String question = "When do hostel gates close for every resident student?";
    assertThat(TestConversations.answer(mvc, faculty, question).outcome()).isEqualTo("ANSWERED");
    assertThat(TestConversations.answer(mvc, faculty, question).outcome()).isEqualTo("ANSWERED");
    assertThat(TestConversations.answer(mvc, faculty, "canteen menu fridays").outcome())
        .isEqualTo("INSUFFICIENT_EVIDENCE");
    assertThat(TestSearch.hits(mvc, faculty, SearchMode.KEYWORD, "hostel gates", null))
        .isNotEmpty();
    assertThat(request(mvc, faculty, left)).hasStatus(HttpStatus.ACCEPTED);
    awaitFinished(mvc, faculty, left);
    // Served by the summary just made: logged as REUSED, with no model run.
    assertThat(request(mvc, faculty, left)).hasStatusOk();
    assertThat(compare(mvc, faculty, left, right)).hasStatusOk();
    awaitSummaryRow();
  }

  /** The summary's query log row is written on the summary pool just after the summary. */
  private void awaitSummaryRow() {
    long deadline = System.nanoTime() + 5_000_000_000L;
    while (System.nanoTime() < deadline) {
      long rows =
          jdbc.sql("SELECT count(*) FROM query_log WHERE user_id = :user AND type = 'SUMMARIZE'")
              .param("user", account.id())
              .query(Long.class)
              .single();
      if (rows > 0) {
        return;
      }
      try {
        Thread.sleep(20);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError(e);
      }
    }
    throw new AssertionError("The summary's query log row was not written");
  }

  private String analytics(int days, String zone) {
    MvcTestResult result =
        TestDocuments.get(mvc, admin, "/api/v1/admin/analytics?days=" + days + "&zone=" + zone);
    assertThat(result).hasStatusOk();
    return TestAccounts.body(result);
  }

  private Map<String, Object> direct(String sql) {
    return jdbc.sql(PERIOD + sql).param("zone", ZONE).query().singleRow();
  }

  private Map<String, Long> grouped(String sql) {
    return jdbc.sql(sql).query().listOfRows().stream()
        .collect(Collectors.toMap(row -> (String) row.get("name"), row -> (Long) row.get("n")));
  }

  private static Map<String, Long> counts(String body, String path) {
    List<Map<String, Object>> rows = JsonPath.read(body, path);
    return rows.stream()
        .filter(row -> ((Number) row.get("count")).longValue() > 0)
        .collect(
            Collectors.toMap(
                row -> (String) row.get("name"), row -> ((Number) row.get("count")).longValue()));
  }

  private static Object number(String body, String path) {
    return ((Number) JsonPath.read(body, path)).longValue();
  }

  private static long asLong(Object value) {
    return ((Number) value).longValue();
  }

  private static String read(String body, String path) {
    return JsonPath.read(body, path);
  }

  private static org.assertj.core.data.Offset<Double> within() {
    return org.assertj.core.data.Offset.offset(1e-9);
  }
}
