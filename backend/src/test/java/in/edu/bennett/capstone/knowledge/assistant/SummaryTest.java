package in.edu.bennett.capstone.knowledge.assistant;

import static in.edu.bennett.capstone.knowledge.assistant.TestSummaries.awaitFinished;
import static in.edu.bennett.capstone.knowledge.assistant.TestSummaries.get;
import static in.edu.bennett.capstone.knowledge.assistant.TestSummaries.indexed;
import static in.edu.bennett.capstone.knowledge.assistant.TestSummaries.request;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import in.edu.bennett.capstone.knowledge.AuditRows;
import in.edu.bennett.capstone.knowledge.FakeChatModel;
import in.edu.bennett.capstone.knowledge.FakeChatModel.Request;
import in.edu.bennett.capstone.knowledge.IntegrationTest;
import in.edu.bennett.capstone.knowledge.TestAccounts;
import in.edu.bennett.capstone.knowledge.TestAccounts.Account;
import in.edu.bennett.capstone.knowledge.TestAccounts.Session;
import in.edu.bennett.capstone.knowledge.ai.ChatFailedException;
import in.edu.bennett.capstone.knowledge.document.TestDocuments;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Summaries (specification 13.1, ADR-040) through the real endpoints, with the scripted chat model:
 * the single pass and the map-reduce path, the checks on every item, the limits refused before any
 * call, the stored summary served only after a fresh access check (ACL-E5) and only while the
 * document is indexed as it was, and the cost line. The access matrix is in {@code
 * SummaryAccessMatrixTest}.
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class SummaryTest {

  /** With the defaults, 1,500 tokens a chunk: two chunks make a group of 3,000. */
  private static final int TOKENS = 1500;

  @Autowired MockMvcTester mvc;

  @Autowired JdbcClient jdbc;

  @Autowired FakeChatModel chat;

  @Autowired SummaryService summaries;

  private Account facultyAccount;
  private Session faculty;

  @BeforeEach
  void signIn() {
    facultyAccount = TestAccounts.registerAs(mvc, jdbc, "FACULTY");
    faculty = TestAccounts.login(mvc, facultyAccount);
  }

  @AfterEach
  void resetTheModel() {
    chat.reset();
  }

  /** SUM-3: text within the single-pass budget takes one call, and pages come from the chunks. */
  @Test
  void aShortDocumentIsSummarizedInOneCall() {
    UUID id =
        indexed(
            mvc,
            jdbc,
            faculty,
            "Lab rules " + UUID.randomUUID(),
            "[]",
            100,
            "The lab exam lasts 55 minutes.",
            "Phones must be switched off before the exam.",
            "Results are published on 9 October 2026.");
    chat.answerNext(
        """
        {"overview": "The rules of the lab exam [1][2].",
         "keyPoints": ["The exam lasts 55 minutes [1].", "Phones are switched off [2]."],
         "rules": ["Phones must be switched off before the exam [2]."],
         "dates": ["Results are published on 9 October 2026 [3]."]}""");

    MvcTestResult started = request(mvc, faculty, id);

    assertThat(started).hasStatus(HttpStatus.ACCEPTED);
    assertThat(started).bodyJson().extractingPath("$.status").isEqualTo("PENDING");
    String body = awaitFinished(mvc, faculty, id);
    assertThat((String) JsonPath.read(body, "$.status")).isEqualTo("READY");
    assertThat((String) JsonPath.read(body, "$.path")).isEqualTo("SINGLE_PASS");
    assertThat((Integer) JsonPath.read(body, "$.modelCalls")).isEqualTo(1);
    assertThat((String) JsonPath.read(body, "$.model")).isEqualTo(FakeChatModel.MODEL);
    assertThat((String) JsonPath.read(body, "$.promptVersion")).isEqualTo("summary-v1");
    assertThat((String) JsonPath.read(body, "$.summary.overview"))
        .isEqualTo("The rules of the lab exam [1][2].");
    assertThat((List<String>) JsonPath.read(body, "$.summary.dates"))
        .containsExactly("Results are published on 9 October 2026 [3].");
    List<Map<String, Object>> citations = JsonPath.read(body, "$.summary.citations");
    assertThat(citations)
        .extracting(c -> c.get("ordinal"), c -> c.get("pageStart"), c -> c.get("snippet"))
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(1, 1, "The lab exam lasts 55 minutes."),
            org.assertj.core.groups.Tuple.tuple(
                2, 2, "Phones must be switched off before the exam."),
            org.assertj.core.groups.Tuple.tuple(3, 3, "Results are published on 9 October 2026."));
    assertThat(citations).allSatisfy(c -> assertThat(c.get("documentId")).isEqualTo(id.toString()));

    assertThat(chat.calls()).isOne();
    Request call = chat.requests().getFirst();
    assertThat(call.system()).contains("You summarize one document");
    assertThat(call.user())
        .contains("<passage n=\"1\" pages=\"1\">", "<passage n=\"3\" pages=\"3\">")
        .contains("Summarize this document.");
    assertThat(call.outputSchema()).isEqualTo(SummaryOutput.SCHEMA);
    assertThat(chat.callsInTransaction()).isZero();

    // Asked again, the stored summary is served without another call (SUM-4).
    MvcTestResult again = request(mvc, faculty, id);
    assertThat(again).hasStatusOk();
    assertThat(again).bodyJson().extractingPath("$.status").isEqualTo("READY");
    assertThat(chat.calls()).isOne();
  }

  /**
   * SUM-3: six chunks of 1,500 tokens are above the 4,000 of a single pass, so they are summarized
   * in three groups of two (map), then combined (reduce): four calls. Each map call sees its own
   * passages only; the reduce call sees the partial summaries, never the text; the markers the
   * reduce keeps name passages, which are renumbered in reading order.
   */
  @Test
  void aLongDocumentIsSummarizedPieceByPiece(CapturedOutput output) {
    UUID id =
        indexed(
            mvc,
            jdbc,
            faculty,
            "Handbook " + UUID.randomUUID(),
            "[]",
            TOKENS,
            "alpha part about admissions",
            "beta part about fees of 4500 rupees",
            "gamma part about attendance of 75 percent",
            "delta part about hostels",
            "epsilon part about exams",
            "zeta part about results on 9 October 2026");
    chat.answerNext(
        """
        {"overview": "Admissions and fees [1][2].", "keyPoints": ["Fees are 4500 rupees [2]."],
         "rules": [], "dates": []}""");
    chat.answerNext(
        """
        {"overview": "Attendance and hostels [3].",
         "keyPoints": ["Attendance must be 75 percent [3].", "Wrong group [1]."],
         "rules": ["At least 75 percent attendance [3]."], "dates": []}""");
    chat.answerNext(
        """
        {"overview": "Exams and results [5][6].", "keyPoints": [],
         "rules": [], "dates": ["Results on 9 October 2026 [6]."]}""");
    chat.answerNext(
        """
        {"overview": "The handbook covers admissions, fees, attendance and results [1][2][3][6].",
         "keyPoints": ["Fees are 4500 rupees [2].", "Attendance must be 75 percent [3]."],
         "rules": ["At least 75 percent attendance [3].", "Invented marker [4]."],
         "dates": ["Results on 9 October 2026 [6]."]}""");

    assertThat(request(mvc, faculty, id)).hasStatus(HttpStatus.ACCEPTED);
    String body = awaitFinished(mvc, faculty, id);

    assertThat((String) JsonPath.read(body, "$.status")).isEqualTo("READY");
    assertThat((String) JsonPath.read(body, "$.path")).isEqualTo("MAP_REDUCE");
    assertThat((Integer) JsonPath.read(body, "$.modelCalls")).isEqualTo(4);
    assertThat(chat.calls()).isEqualTo(4);
    List<Request> calls = chat.requests();
    assertThat(calls.get(0).user())
        .contains("part=\"1 of 3\"", "alpha part", "beta part")
        .doesNotContain("gamma part", "zeta part");
    assertThat(calls.get(1).user())
        .contains("part=\"2 of 3\"", "gamma part", "delta part")
        .doesNotContain("alpha part", "epsilon part");
    assertThat(calls.get(2).user()).contains("part=\"3 of 3\"", "epsilon part", "zeta part");
    Request reduce = calls.get(3);
    assertThat(reduce.system()).contains("You combine partial summaries");
    assertThat(reduce.user())
        .contains("<partial n=\"1\" passages=\"1-2\">", "Fees are 4500 rupees [2].")
        .contains("At least 75 percent attendance [3].")
        // The map item citing a passage of another group was dropped before the reduce.
        .doesNotContain("Wrong group")
        .doesNotContain("alpha part", "zeta part");
    assertThat(chat.callsInTransaction()).isZero();

    // Passages 1, 2, 3 and 6 are cited, in that order of reading: renumbered 1, 2, 3, 4. Marker [4]
    // named passage 4, which no partial summary cited, so its item was dropped.
    assertThat((String) JsonPath.read(body, "$.summary.overview"))
        .isEqualTo("The handbook covers admissions, fees, attendance and results [1][2][3][4].");
    assertThat((List<String>) JsonPath.read(body, "$.summary.rules"))
        .containsExactly("At least 75 percent attendance [3].");
    assertThat((List<String>) JsonPath.read(body, "$.summary.dates"))
        .containsExactly("Results on 9 October 2026 [4].");
    List<Map<String, Object>> citations = JsonPath.read(body, "$.summary.citations");
    assertThat(citations).extracting(c -> c.get("pageStart")).containsExactly(1, 2, 3, 6);

    assertThat(output.getAll())
        .contains("Summary user=" + facultyAccount.id() + " document=" + id)
        .contains("outcome=READY path=MAP_REDUCE groups=3 textTokens=9000 modelCalls=4")
        .contains("inputTokens=4800")
        .contains("outputTokens=320")
        // 4 calls of 1,200 tokens in and 80 out: 4,800 x $0.10 + 320 x $0.50, per million.
        .contains("costUsd=0.000640")
        // Kept: the overview, two key points, a rule and a date. Dropped by the checks: "Wrong
        // group" in the map step and "Invented marker" in the reduce; merged items are not dropped.
        .contains("itemsKept=5 itemsDropped=2")
        .doesNotContain("alpha part");
    assertThat(AuditRows.forResource(jdbc, id.toString()))
        .extracting(row -> row.get("action"))
        .contains("SUMMARY_REQUESTED");
  }

  /**
   * The checks on every item: a marker of a passage that was not sent is removed, an item left
   * without one is dropped, and so is an item with a number its passages do not hold.
   */
  @Test
  void itemsNotTiedToTheirPassagesAreDropped() {
    UUID id =
        indexed(
            mvc,
            jdbc,
            faculty,
            "Rules " + UUID.randomUUID(),
            "[]",
            100,
            "The fee is 4500 rupees.",
            "Attendance of 75 percent is required.");
    chat.answerNext(
        """
        {"overview": "Fees and attendance [1][2][9].",
         "keyPoints": ["The fee is 4500 rupees [1].", "No marker here.",
                       "The fee is 5000 rupees [1].", "Attendance of 75 percent [7]."],
         "rules": ["Attendance of 75 percent is required [2]."], "dates": []}""");

    request(mvc, faculty, id);
    String body = awaitFinished(mvc, faculty, id);

    assertThat((String) JsonPath.read(body, "$.summary.overview"))
        .isEqualTo("Fees and attendance [1][2].");
    assertThat((List<String>) JsonPath.read(body, "$.summary.keyPoints"))
        .containsExactly("The fee is 4500 rupees [1].");
    assertThat((List<String>) JsonPath.read(body, "$.summary.rules"))
        .containsExactly("Attendance of 75 percent is required [2].");
  }

  /**
   * A summary with nothing left after the checks, or malformed output, fails and can be retried.
   */
  @Test
  void aSummaryThatFailsSaysWhyAndCanBeRetried() {
    UUID id =
        indexed(mvc, jdbc, faculty, "Notice " + UUID.randomUUID(), "[]", 100, "Hostel closes.");
    chat.answerNext("{\"overview\": \"No marker.\", \"keyPoints\": [], \"rules\": []}");

    request(mvc, faculty, id);
    String failed = awaitFinished(mvc, faculty, id);

    assertThat((String) JsonPath.read(failed, "$.status")).isEqualTo("FAILED");
    assertThat((String) JsonPath.read(failed, "$.failureMessage"))
        .isEqualTo("The chat model's output was malformed.");

    chat.failNext(() -> new RuntimeException("timeout"));
    assertThat(request(mvc, faculty, id)).hasStatus(HttpStatus.ACCEPTED);
    String error = awaitFinished(mvc, faculty, id);
    assertThat((String) JsonPath.read(error, "$.failureMessage"))
        .isEqualTo("The chat model gave no answer.");

    chat.answerNext(
        """
        {"overview": "No marker.", "keyPoints": ["Also none."], "rules": [], "dates": []}""");
    request(mvc, faculty, id);
    assertThat((String) JsonPath.read(awaitFinished(mvc, faculty, id), "$.failureMessage"))
        .isEqualTo("The model's summary had no item tied to the document's text.");

    request(mvc, faculty, id);
    assertThat((String) JsonPath.read(awaitFinished(mvc, faculty, id), "$.status"))
        .isEqualTo("READY");
    assertThat(chat.calls()).isEqualTo(4);
  }

  /** The limits are checked before any call: pages, then the calls the text would need. */
  @Test
  void aDocumentAboveTheLimitsIsRefusedBeforeAnyModelCall() {
    UUID manyPages =
        indexed(mvc, jdbc, faculty, "Long " + UUID.randomUUID(), "[]", 100, "Some text.");
    jdbc.sql("UPDATE document SET page_count = 41 WHERE id = :id").param("id", manyPages).update();
    String[] chunks = new String[16];
    for (int i = 0; i < chunks.length; i++) {
      chunks[i] = "chunk " + i;
    }
    // 16 chunks of 1,500 tokens make 8 groups: 9 calls, one more than the limit of 8.
    UUID manyCalls =
        indexed(mvc, jdbc, faculty, "Dense " + UUID.randomUUID(), "[]", TOKENS, chunks);
    jdbc.sql("UPDATE document SET page_count = 16 WHERE id = :id").param("id", manyCalls).update();

    MvcTestResult pages = request(mvc, faculty, manyPages);
    MvcTestResult calls = request(mvc, faculty, manyCalls);

    assertThat(pages).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(pages).bodyJson().extractingPath("$.code").isEqualTo("DOCUMENT_TOO_LARGE");
    assertThat(pages)
        .bodyJson()
        .extractingPath("$.detail")
        .isEqualTo("This document has 41 pages. Summaries are limited to 40 pages.");
    assertThat(calls).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(calls)
        .bodyJson()
        .extractingPath("$.detail")
        .isEqualTo(
            "This document is too long to summarize: it would need 9 model calls, and a summary"
                + " may make at most 8.");
    assertThat(chat.calls()).isZero();
    assertThat(get(mvc, faculty, manyPages))
        .bodyJson()
        .extractingPath("$.status")
        .isEqualTo("NONE");
  }

  /** SUM-1: a document that is not indexed is refused. */
  @Test
  void aDocumentThatIsNotIndexedIsRefused() {
    UUID id = TestDocuments.uploaded(mvc, faculty, "Waiting " + UUID.randomUUID(), "[]");

    MvcTestResult result = request(mvc, faculty, id);

    assertThat(result).hasStatus(HttpStatus.CONFLICT);
    assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("DOCUMENT_NOT_INDEXED");
    assertThat(get(mvc, faculty, id)).bodyJson().extractingPath("$.status").isEqualTo("NONE");
    assertThat(chat.calls()).isZero();
  }

  /** SUM-4: re-indexing makes a stored summary out of date; the next request makes a new one. */
  @Test
  void reIndexingMakesTheStoredSummaryOutOfDate() {
    UUID id = indexed(mvc, jdbc, faculty, "Policy " + UUID.randomUUID(), "[]", 100, "Rule one.");
    request(mvc, faculty, id);
    assertThat((String) JsonPath.read(awaitFinished(mvc, faculty, id), "$.status"))
        .isEqualTo("READY");

    jdbc.sql("UPDATE document SET indexed_at = now() + interval '1 second' WHERE id = :id")
        .param("id", id)
        .update();

    assertThat(get(mvc, faculty, id)).bodyJson().extractingPath("$.status").isEqualTo("NONE");
    assertThat(request(mvc, faculty, id)).hasStatus(HttpStatus.ACCEPTED);
    assertThat((String) JsonPath.read(awaitFinished(mvc, faculty, id), "$.status"))
        .isEqualTo("READY");
    assertThat(chat.calls()).isEqualTo(2);
  }

  /**
   * ACL-E5: a stored summary is served only after a fresh access check. The student reads the
   * summary the owner had made; once the grant is removed, the summary answers 404 like the
   * document itself, and so does asking for a new one.
   */
  @Test
  void aStoredSummaryIsRefusedOnceTheReaderLosesAccess() {
    Account studentAccount = TestAccounts.registerStudent(mvc);
    Session student = TestAccounts.login(mvc, studentAccount);
    String grant = "[{\"type\": \"USER\", \"userId\": \"%s\"}]".formatted(studentAccount.id());
    UUID id =
        indexed(mvc, jdbc, faculty, "Shared " + UUID.randomUUID(), grant, 100, "Shared rule.");
    request(mvc, faculty, id);
    awaitFinished(mvc, faculty, id);

    MvcTestResult read = get(mvc, student, id);
    assertThat(read).hasStatusOk();
    assertThat(read).bodyJson().extractingPath("$.status").isEqualTo("READY");
    assertThat(read)
        .bodyJson()
        .extractingPath("$.summary.citations[0].snippet")
        .isEqualTo("Shared rule.");

    assertThat(
            TestDocuments.send(
                mvc,
                faculty,
                HttpMethod.PUT,
                "/api/v1/documents/" + id + "/grants",
                "{\"grants\": []}"))
        .hasStatusOk();

    for (MvcTestResult refused : List.of(get(mvc, student, id), request(mvc, student, id))) {
      assertThat(refused).hasStatus(HttpStatus.NOT_FOUND);
      assertThat(refused).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
      assertThat(TestAccounts.body(refused)).doesNotContain("Shared rule", "summary\":{");
    }
    assertThat(chat.calls()).isOne();
  }

  /** SUM-5: two requests at once start one run; the second answers that it is being made. */
  @Test
  void twoRequestsAtOnceStartOneRun() throws InterruptedException {
    UUID id = indexed(mvc, jdbc, faculty, "Busy " + UUID.randomUUID(), "[]", 100, "One rule.");
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch started =
        chat.holdNext(
            release,
            () ->
                """
                {"overview": "One rule [1].", "keyPoints": [], "rules": [], "dates": []}""");

    assertThat(request(mvc, faculty, id)).hasStatus(HttpStatus.ACCEPTED);
    assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
    MvcTestResult second = request(mvc, faculty, id);
    release.countDown();

    assertThat(second).hasStatus(HttpStatus.ACCEPTED);
    assertThat(second).bodyJson().extractingPath("$.status").isEqualTo("PENDING");
    assertThat((String) JsonPath.read(awaitFinished(mvc, faculty, id), "$.status"))
        .isEqualTo("READY");
    assertThat(chat.calls()).isOne();
  }

  /** A summary still marked as being made when the application starts failed with the restart. */
  @Test
  void aSummaryInterruptedByARestartIsMarkedFailed() {
    UUID id = indexed(mvc, jdbc, faculty, "Stuck " + UUID.randomUUID(), "[]", 100, "Text.");
    jdbc.sql(
            """
            INSERT INTO document_summary (document_id, model, prompt_version, status,
                source_indexed_at, started_at, created_at, updated_at)
            SELECT id, :model, 'summary-v1', 'PENDING', indexed_at, now(), now(), now()
            FROM document WHERE id = :id
            """)
        .param("id", id)
        .param("model", FakeChatModel.MODEL)
        .update();
    assertThat(get(mvc, faculty, id)).bodyJson().extractingPath("$.status").isEqualTo("PENDING");

    summaries.failInterrupted();

    MvcTestResult result = get(mvc, faculty, id);
    assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("FAILED");
    assertThat(result)
        .bodyJson()
        .extractingPath("$.failureMessage")
        .isEqualTo("The summary was interrupted by a restart. Try again.");
  }

  /** DATA-2: deleting the document deletes its summaries. */
  @Test
  void deletingTheDocumentDeletesItsSummary() {
    UUID id = indexed(mvc, jdbc, faculty, "Gone " + UUID.randomUUID(), "[]", 100, "Text.");
    request(mvc, faculty, id);
    awaitFinished(mvc, faculty, id);

    assertThat(TestDocuments.send(mvc, faculty, HttpMethod.DELETE, "/api/v1/documents/" + id, null))
        .hasStatus(HttpStatus.NO_CONTENT);

    assertThat(
            jdbc.sql("SELECT count(*) FROM document_summary WHERE document_id = :id")
                .param("id", id)
                .query(Integer.class)
                .single())
        .isZero();
  }

  /** A provider failure inside the run is a failed summary, never a 500 to anyone. */
  @Test
  void aChatModelFailureIsAFailedSummary() {
    UUID id = indexed(mvc, jdbc, faculty, "Down " + UUID.randomUUID(), "[]", 100, "Text.");
    chat.failNext(() -> new ChatFailedException("down"));

    assertThat(request(mvc, faculty, id)).hasStatus(HttpStatus.ACCEPTED);

    assertThat((String) JsonPath.read(awaitFinished(mvc, faculty, id), "$.status"))
        .isEqualTo("FAILED");
  }
}
