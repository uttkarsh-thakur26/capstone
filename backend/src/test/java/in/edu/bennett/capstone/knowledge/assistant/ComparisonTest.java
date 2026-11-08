package in.edu.bennett.capstone.knowledge.assistant;

import static in.edu.bennett.capstone.knowledge.assistant.TestSummaries.compare;
import static in.edu.bennett.capstone.knowledge.assistant.TestSummaries.indexed;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import in.edu.bennett.capstone.knowledge.FakeChatModel;
import in.edu.bennett.capstone.knowledge.IntegrationTest;
import in.edu.bennett.capstone.knowledge.TestAccounts;
import in.edu.bennett.capstone.knowledge.TestAccounts.Account;
import in.edu.bennett.capstone.knowledge.TestAccounts.Session;
import in.edu.bennett.capstone.knowledge.document.TestDocuments;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * Comparing two documents (specification 13.2, ADR-040) through the real endpoint, with the
 * scripted chat model. The fixture is two versions of an exam notice: one passage the same, one
 * requirement changed, one date changed and one rule added. The model reports those three and five
 * that must not survive: a quote that is not in its passage, an "added" text that the left version
 * holds, a number that is in neither quote, an unknown candidate and a removed text taken from a
 * passage that is not on the left. The access matrix is in {@code ComparisonAccessMatrixTest}.
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class ComparisonTest {

  private static final String SAME = "The lab exam lasts 55 minutes for every student.";

  @Autowired MockMvcTester mvc;

  @Autowired JdbcClient jdbc;

  @Autowired FakeChatModel chat;

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

  private UUID version2025() {
    return indexed(
        mvc,
        jdbc,
        faculty,
        "Exam notice 2025 " + UUID.randomUUID(),
        "[]",
        20,
        SAME,
        "Phones must be switched off during the exam.",
        "Results are published on 9 October 2026 on the portal.");
  }

  private UUID version2026() {
    return indexed(
        mvc,
        jdbc,
        faculty,
        "Exam notice 2026 " + UUID.randomUUID(),
        "[]",
        20,
        SAME,
        "Phones must be switched off during the exam and kept in bags.",
        "Results are published on 12 October 2026 on the portal.",
        "Calculators of any kind are allowed.");
  }

  /** CMP-2 to CMP-4: real differences with pages on both sides, and nothing unsupported. */
  @Test
  void realDifferencesComeWithPagesOnBothSidesAndUnsupportedOnesAreDiscarded(
      CapturedOutput output) {
    UUID left = version2025();
    UUID right = version2026();
    chat.answerNext(
        """
        {"differences": [
          {"candidate": 2, "category": "CHANGED_DATE",
           "leftQuote": "published on 9 October 2026", "rightQuote": "PUBLISHED ON 12 October 2026",
           "explanation": "Results move from 9 October 2026 to 12 October 2026."},
          {"candidate": 3, "category": "ADDED", "leftQuote": "",
           "rightQuote": "Calculators of any kind are allowed.",
           "explanation": "Calculators are now allowed."},
          {"candidate": 1, "category": "CHANGED_REQUIREMENT",
           "leftQuote": "switched off during the exam.", "rightQuote": "and kept in bags",
           "explanation": "Phones must now also be kept in bags."},
          {"candidate": 2, "category": "CHANGED_THRESHOLD",
           "leftQuote": "published on 10 October", "rightQuote": "published on 12 October",
           "explanation": "The date moved."},
          {"candidate": 1, "category": "ADDED", "leftQuote": "",
           "rightQuote": "Phones must be switched off",
           "explanation": "Phones must be switched off."},
          {"candidate": 2, "category": "CHANGED_DATE",
           "leftQuote": "published on 9 October", "rightQuote": "published on 12 October",
           "explanation": "Results move by 5 days."},
          {"candidate": 99, "category": "OTHER", "leftQuote": "a", "rightQuote": "b",
           "explanation": "Unknown."},
          {"candidate": 3, "category": "REMOVED", "leftQuote": "Calculators of any kind",
           "rightQuote": "", "explanation": "Calculators were removed."}
        ]}""");

    MvcTestResult result = compare(mvc, faculty, left, right);

    assertThat(result).hasStatusOk();
    String body = TestAccounts.body(result);
    assertThat((String) JsonPath.read(body, "$.left.id")).isEqualTo(left.toString());
    assertThat((String) JsonPath.read(body, "$.right.id")).isEqualTo(right.toString());
    assertThat((Integer) JsonPath.read(body, "$.identicalPassages")).isEqualTo(1);
    assertThat((Integer) JsonPath.read(body, "$.passagesCompared")).isEqualTo(3);
    assertThat((Integer) JsonPath.read(body, "$.differencesDropped")).isEqualTo(5);
    assertThat((Integer) JsonPath.read(body, "$.modelCalls")).isEqualTo(1);
    List<Map<String, Object>> differences = JsonPath.read(body, "$.differences");
    assertThat(differences)
        .extracting(d -> d.get("category"))
        .containsExactly("CHANGED_DATE", "ADDED", "CHANGED_REQUIREMENT");

    // The quote shown is the stored text, with its own case, not the model's copy of it.
    assertThat((String) JsonPath.read(body, "$.differences[0].left.quote"))
        .isEqualTo("published on 9 October 2026");
    assertThat((String) JsonPath.read(body, "$.differences[0].right.quote"))
        .isEqualTo("published on 12 October 2026");
    assertThat((Integer) JsonPath.read(body, "$.differences[0].left.citation.pageStart"))
        .isEqualTo(3);
    assertThat((Integer) JsonPath.read(body, "$.differences[0].right.citation.pageStart"))
        .isEqualTo(3);
    assertThat((String) JsonPath.read(body, "$.differences[0].left.citation.documentId"))
        .isEqualTo(left.toString());
    assertThat((String) JsonPath.read(body, "$.differences[0].right.citation.documentId"))
        .isEqualTo(right.toString());
    // Present on one side only: the left side is absent, the right one has its page.
    assertThat((Object) JsonPath.read(body, "$.differences[1].left")).isNull();
    assertThat((Integer) JsonPath.read(body, "$.differences[1].right.citation.pageStart"))
        .isEqualTo(4);

    // Diff first: the passage that reads the same was never sent; the others were, once.
    assertThat(chat.calls()).isOne();
    String prompt = chat.requests().getFirst().user();
    assertThat(prompt)
        .doesNotContain(SAME)
        .contains("<candidate n=\"1\">", "<candidate n=\"3\">")
        .contains("kept in bags", "Calculators of any kind");
    assertThat(chat.requests().getFirst().outputSchema()).isEqualTo(Comparer.SCHEMA);
    assertThat(chat.callsInTransaction()).isZero();

    assertThat(output.getAll())
        .contains("Comparison user=" + facultyAccount.id() + " left=" + left + " right=" + right)
        .contains("outcome=COMPARED candidates=3 identical=1 modelCalls=1")
        // 1,200 tokens in and 80 out: 1,200 x $0.10 + 80 x $0.50, per million.
        .contains("costUsd=0.000160")
        .contains("differences=3 dropped=5")
        .doesNotContain("kept in bags");
  }

  /** Two documents that read the same need no model call and report no difference. */
  @Test
  void documentsThatReadTheSameNeedNoModelCall() {
    UUID left = indexed(mvc, jdbc, faculty, "A " + UUID.randomUUID(), "[]", 20, SAME);
    UUID right = indexed(mvc, jdbc, faculty, "B " + UUID.randomUUID(), "[]", 20, "  " + SAME);

    MvcTestResult result = compare(mvc, faculty, left, right);

    assertThat(result).hasStatusOk();
    assertThat(result).bodyJson().extractingPath("$.differences").asArray().isEmpty();
    assertThat(result).bodyJson().extractingPath("$.modelCalls").isEqualTo(0);
    assertThat(result).bodyJson().extractingPath("$.identicalPassages").isEqualTo(1);
    assertThat(chat.calls()).isZero();
  }

  /** CMP-5: above the page or token limit, a clear refusal before any model call. */
  @Test
  void documentsAboveTheLimitsAreTooLargeToCompare() {
    UUID left = version2025();
    UUID right = version2026();
    jdbc.sql("UPDATE document SET page_count = 31 WHERE id = :id").param("id", right).update();
    UUID dense = indexed(mvc, jdbc, faculty, "Dense " + UUID.randomUUID(), "[]", 9000, "x", "y");

    MvcTestResult pages = compare(mvc, faculty, left, right);
    MvcTestResult tokens = compare(mvc, faculty, left, dense);

    assertThat(pages).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(pages).bodyJson().extractingPath("$.code").isEqualTo("DOCUMENT_TOO_LARGE");
    assertThat((String) JsonPath.read(TestAccounts.body(pages), "$.detail"))
        .endsWith("has 31 pages, too large to compare: the limit is 30 pages per document.");
    assertThat(tokens).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat((String) JsonPath.read(TestAccounts.body(tokens), "$.detail"))
        .startsWith("These documents are too large to compare");
    assertThat(chat.calls()).isZero();
  }

  /** CMP-1 and 15.1: two different documents, both indexed. */
  @Test
  void aComparisonNeedsTwoIndexedDocuments() {
    UUID left = version2025();
    UUID waiting = TestDocuments.uploaded(mvc, faculty, "Waiting " + UUID.randomUUID(), "[]");

    MvcTestResult same = compare(mvc, faculty, left, left);
    MvcTestResult notIndexed = compare(mvc, faculty, left, waiting);
    MvcTestResult missing = compare(mvc, faculty, UUID.randomUUID(), left);

    assertThat(same).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(same).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
    assertThat(notIndexed).hasStatus(HttpStatus.CONFLICT);
    assertThat(notIndexed).bodyJson().extractingPath("$.code").isEqualTo("DOCUMENT_NOT_INDEXED");
    assertThat(missing).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(chat.calls()).isZero();
  }

  /** Malformed output and a provider failure answer 503, never a partial result. */
  @Test
  void aModelThatFailsAnswers503() {
    UUID left = version2025();
    UUID right = version2026();
    chat.answerNext("{\"differences\": [{\"candidate\": \"two\"}]}");
    chat.failNext(() -> new RuntimeException("timeout"));

    MvcTestResult malformed = compare(mvc, faculty, left, right);
    MvcTestResult failed = compare(mvc, faculty, left, right);

    for (MvcTestResult result : List.of(malformed, failed)) {
      assertThat(result).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
      assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MODEL_UNAVAILABLE");
    }
  }
}
