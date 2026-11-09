package in.edu.bennett.capstone.knowledge.document;

import static in.edu.bennett.capstone.knowledge.TestAccounts.body;
import static in.edu.bennett.capstone.knowledge.TestAccounts.login;
import static in.edu.bennett.capstone.knowledge.TestAccounts.registerAs;
import static in.edu.bennett.capstone.knowledge.document.TestDocuments.send;
import static in.edu.bennett.capstone.knowledge.document.TestDocuments.upload;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import in.edu.bennett.capstone.knowledge.IntegrationTest;
import in.edu.bennett.capstone.knowledge.TestAccounts.Session;
import in.edu.bennett.capstone.knowledge.TestPdfs;
import in.edu.bennett.capstone.knowledge.retrieval.TestSearch;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * The rules for a document's title, description, category and date (DOC-1, DOC-7, ADR-040), on
 * upload and on edit, where the server is the real check and the forms only give quick feedback. A
 * refused request answers 400 {@code VALIDATION_FAILED} naming the field, and nothing is stored.
 * Also: the upload time comes from the server alone.
 */
@IntegrationTest
class DocumentMetadataRulesTest {

  @Autowired MockMvcTester mvc;

  @Autowired JdbcClient jdbc;

  private Session faculty;

  @BeforeEach
  void signIn() {
    faculty = login(mvc, registerAs(mvc, jdbc, "FACULTY"));
  }

  // Title

  @Test
  void aTitleIsStoredStrippedWithRunsOfSpaceMadeOne() {
    MvcTestResult result = uploadWith("{\"title\": \"  Lab   exam \\t\\n rules\\u00a0 \"}");

    assertThat(result).hasStatus(HttpStatus.CREATED);
    assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Lab exam rules");
  }

  @ParameterizedTest
  @ValueSource(strings = {"Abc", "नियम 2026", "Exam rules 2026-27 (revised)", "B.Tech CSE"})
  void aTitleWithAtLeastThreeLettersIsAccepted(String title) {
    assertThat(uploadWith("{\"title\": " + TestSearch.quoted(title) + "}"))
        .hasStatus(HttpStatus.CREATED);
  }

  @Test
  void aTitleOfExactlyTwoHundredCharactersIsAcceptedAndOneMoreIsNot() {
    assertThat(uploadWith("{\"title\": \"" + "a".repeat(200) + "\"}"))
        .hasStatus(HttpStatus.CREATED);
    assertRefused(
        uploadWith("{\"title\": \"" + "a".repeat(201) + "\"}"),
        "title",
        "must be 3 to 200 characters long");
  }

  @Test
  void aMissingOrBlankTitleIsRefused() {
    assertRefused(uploadWith("{\"description\": \"no title\"}"), "title", "must not be blank");
    assertRefused(uploadWith("{\"title\": \"   \\t \"}"), "title", "must not be blank");
  }

  @Test
  void aTitleShorterThanThreeCharactersIsRefused() {
    assertRefused(uploadWith("{\"title\": \" ab \"}"), "title", "must be 3 to 200 characters long");
  }

  @ParameterizedTest
  @ValueSource(strings = {"@@@###", "1111", "A1 B2 33", "--- ... ---", "१२३४"})
  void aTitleWithFewerThanThreeLettersIsRefused(String title) {
    assertRefused(
        uploadWith("{\"title\": " + TestSearch.quoted(title) + "}"),
        "title",
        "must contain at least 3 letters");
  }

  // Description

  @Test
  void aDescriptionIsStrippedAndABlankOneIsStoredAsNone() {
    MvcTestResult stripped =
        uploadWith("{\"title\": \"Exam rules\", \"description\": \"  Two  lines\\nhere  \"}");
    MvcTestResult blank = uploadWith("{\"title\": \"Exam rules\", \"description\": \"   \"}");

    assertThat(stripped).bodyJson().extractingPath("$.description").isEqualTo("Two  lines\nhere");
    assertThat(blank).bodyJson().extractingPath("$.description").isNull();
  }

  @Test
  void aDescriptionOfAtMostTwoThousandCharactersIsAccepted() {
    assertThat(
            uploadWith(
                "{\"title\": \"Exam rules\", \"description\": \"" + "d".repeat(2000) + "\"}"))
        .hasStatus(HttpStatus.CREATED);
    assertRefused(
        uploadWith("{\"title\": \"Exam rules\", \"description\": \"" + "d".repeat(2001) + "\"}"),
        "description",
        "size must be between 0 and 2000");
  }

  // Category

  @ParameterizedTest
  @EnumSource(DocumentCategory.class)
  void everyCategoryOfTheListIsAcceptedAndShownAsItsName(DocumentCategory category) {
    MvcTestResult result =
        uploadWith("{\"title\": \"Exam rules\", \"category\": \"" + category.name() + "\"}");

    assertThat(result).hasStatus(HttpStatus.CREATED);
    assertThat(result).bodyJson().extractingPath("$.category").isEqualTo(category.name());
  }

  @ParameterizedTest
  @ValueSource(strings = {"SPORTS", "Policy", "policy", "Course plan", ""})
  void aCategoryOutsideTheListIsRefused(String category) {
    assertRefused(
        uploadWith(
            "{\"title\": \"Exam rules\", \"category\": " + TestSearch.quoted(category) + "}"),
        "category",
        "must be one of POLICY, SYLLABUS, COURSE_PLAN, EXAMINATION, NOTICE, SCHEDULE, HANDBOOK,"
            + " OTHER");
  }

  @Test
  void aCategoryOfTheWrongKindIsRefused() {
    assertRefused(
        uploadWith("{\"title\": \"Exam rules\", \"category\": 7}"), "category", "must be one of");
    assertRefused(
        send(mvc, faculty, HttpMethod.GET, "/api/v1/documents?category=SPORTS", null),
        "category",
        "must be one of POLICY");
  }

  // Document date

  @Test
  void aDateFromTheYear2000ToOneYearAheadIsAccepted() {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    for (LocalDate date :
        List.of(LocalDate.of(2000, 1, 1), LocalDate.of(2024, 2, 29), today, today.plusYears(1))) {
      MvcTestResult result = uploadWith(withDate("\"" + date + "\""));
      assertThat(result).as(date.toString()).hasStatus(HttpStatus.CREATED);
      assertThat(result).bodyJson().extractingPath("$.documentDate").isEqualTo(date.toString());
    }
  }

  @Test
  void aDateBefore2000OrMoreThanAYearAheadIsRefused() {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    for (LocalDate date :
        List.of(
            LocalDate.of(1999, 12, 31),
            LocalDate.of(1900, 1, 1),
            today.plusYears(1).plusDays(2),
            LocalDate.of(9999, 12, 31))) {
      assertRefused(
          uploadWith(withDate("\"" + date + "\"")),
          "documentDate",
          "must be between 2000-01-01 and ");
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "\"2026-02-30\"",
        "\"2025-02-29\"",
        "\"2026-13-01\"",
        "\"2026-00-10\"",
        "\"20266-01-01\"",
        "\"01/07/2026\"",
        "\"tomorrow\"",
        "20260701",
        "true"
      })
  void aDateThatDoesNotExistOrIsNotADateIsRefusedNamingTheField(String value) {
    assertRefused(
        uploadWith(withDate(value)), "documentDate", "must be a real date, written as YYYY-MM-DD");
  }

  // The same rules on edit

  @Test
  void anEditFollowsTheSameRules() {
    UUID id = uploaded();
    String later = LocalDate.now(ZoneOffset.UTC).plusYears(2).toString();

    assertRefused(patch(id, "{\"title\": \"1111\"}"), "title", "must contain at least 3 letters");
    assertRefused(patch(id, "{\"title\": \"   \"}"), "title", "must not be blank");
    assertRefused(
        patch(id, "{\"title\": \"Exam rules\", \"category\": \"Rules\"}"),
        "category",
        "must be one of");
    assertRefused(
        patch(id, "{\"title\": \"Exam rules\", \"documentDate\": \"" + later + "\"}"),
        "documentDate",
        "must be between 2000-01-01 and ");
    assertRefused(
        patch(id, "{\"title\": \"Exam rules\", \"documentDate\": \"2026-02-30\"}"),
        "documentDate",
        "must be a real date");
    assertRefused(
        patch(id, "{\"title\": \"Exam rules\", \"description\": \"" + "d".repeat(2001) + "\"}"),
        "description",
        "size must be between 0 and 2000");

    MvcTestResult valid =
        patch(
            id,
            "{\"title\": \" Exam   rules 2026 \", \"category\": \"NOTICE\","
                + " \"documentDate\": \"2026-07-01\", \"description\": \" New \"}");
    assertThat(valid).hasStatusOk();
    assertThat(valid)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"title": "Exam rules 2026", "category": "NOTICE", "documentDate": "2026-07-01",
             "description": "New"}
            """);
  }

  @Test
  void aRefusedEditChangesNothing() {
    UUID id = uploaded();

    assertRefused(patch(id, "{\"title\": \"@@@\"}"), "title", "must contain at least 3 letters");

    Map<String, Object> row =
        jdbc.sql("SELECT title FROM document WHERE id = :id").param("id", id).query().singleRow();
    assertThat(row.get("title")).isEqualTo("Original title");
  }

  // Upload time

  /**
   * The upload time is the server's: a time in the request is not read, and the time returned is an
   * instant in UTC, written with its {@code Z}, between the moments the request was sent and
   * answered. The screens show it in the reader's own time zone.
   */
  @Test
  void theUploadTimeComesFromTheServerInUtc() {
    Instant before = Instant.now();
    MvcTestResult result =
        uploadWith(
            "{\"title\": \"Exam rules\", \"createdAt\": \"2000-01-01T00:00:00Z\","
                + " \"updatedAt\": \"2000-01-01T00:00:00Z\"}");
    Instant after = Instant.now();

    assertThat(result).hasStatus(HttpStatus.CREATED);
    String createdAt = JsonPath.read(body(result), "$.createdAt");
    assertThat(createdAt).endsWith("Z");
    // The server clock ticks in whole microseconds, so the lower bound is cut to them too.
    assertThat(Instant.parse(createdAt)).isBetween(before.truncatedTo(ChronoUnit.MICROS), after);
    UUID id = UUID.fromString(JsonPath.read(body(result), "$.id"));
    Instant stored =
        jdbc.sql("SELECT created_at FROM document WHERE id = :id")
            .param("id", id)
            .query(Instant.class)
            .single();
    assertThat(stored).isEqualTo(Instant.parse(createdAt));
  }

  private MvcTestResult uploadWith(String metadata) {
    return upload(mvc, faculty, TestPdfs.unique(), "a.pdf", metadata);
  }

  private static String withDate(String value) {
    return "{\"title\": \"Exam rules\", \"documentDate\": " + value + "}";
  }

  private UUID uploaded() {
    MvcTestResult result = uploadWith("{\"title\": \"Original title\"}");
    assertThat(result).hasStatus(HttpStatus.CREATED);
    return UUID.fromString(JsonPath.read(body(result), "$.id"));
  }

  private MvcTestResult patch(UUID id, String json) {
    return send(mvc, faculty, HttpMethod.PATCH, "/api/v1/documents/" + id, json);
  }

  private static void assertRefused(MvcTestResult result, String field, String message) {
    assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
    List<Map<String, String>> errors = JsonPath.read(body(result), "$.errors");
    assertThat(errors)
        .as(body(result))
        .anySatisfy(
            error -> {
              assertThat(error.get("field")).isEqualTo(field);
              assertThat(error.get("message")).startsWith(message);
            });
  }
}
