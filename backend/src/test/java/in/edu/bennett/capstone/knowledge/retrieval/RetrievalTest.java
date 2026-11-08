package in.edu.bennett.capstone.knowledge.retrieval;

import static in.edu.bennett.capstone.knowledge.retrieval.TestSearch.documentIds;
import static in.edu.bennett.capstone.knowledge.retrieval.TestSearch.hits;
import static in.edu.bennett.capstone.knowledge.retrieval.TestSearch.search;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import in.edu.bennett.capstone.knowledge.FakeEmbeddingModel;
import in.edu.bennett.capstone.knowledge.IntegrationTest;
import in.edu.bennett.capstone.knowledge.TestAccounts;
import in.edu.bennett.capstone.knowledge.TestAccounts.Account;
import in.edu.bennett.capstone.knowledge.TestAccounts.Session;
import in.edu.bennett.capstone.knowledge.ai.EmbeddingProperties;
import in.edu.bennett.capstone.knowledge.document.AccessMatrix;
import in.edu.bennett.capstone.knowledge.document.TestDocuments;
import in.edu.bennett.capstone.knowledge.retrieval.TestSearch.Hit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * The "Retrieval" tests of specification 19.2, in both modes, plus the rules of Section 10 that
 * they leave out. The test database is shared, so every test builds its own documents and uses
 * words made up for it, which no other chunk contains. Chunks are written directly with the fake
 * model's vectors of their text (19.1); {@code SearchAfterIngestionTest} covers the way through
 * real ingestion.
 */
@IntegrationTest
class RetrievalTest {

  @Autowired MockMvcTester mvc;

  @Autowired JdbcClient jdbc;

  @Autowired FakeEmbeddingModel model;

  @Autowired EmbeddingProperties embedding;

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
  void resetTheModel() {
    model.reset();
  }

  /** 19.2: a known query returns the expected chunk in the top k. */
  @ParameterizedTest
  @EnumSource(SearchMode.class)
  void aKnownQueryReturnsTheExpectedChunk(SearchMode mode) {
    String[] w = words(8);
    UUID id =
        indexed(
            toStudent(),
            w[0] + " " + w[1] + " " + w[2],
            w[3] + " " + w[4] + " " + w[5],
            w[6] + " " + w[7]);

    List<Hit> found = hits(mvc, student, mode, w[4] + " " + w[5], 6);

    assertThat(found).first().satisfies(hit -> assertThat(hit.documentId()).isEqualTo(id));
    assertThat(found.getFirst().pageStart()).isEqualTo(2);
    assertThat(found.getFirst().text()).isEqualTo(w[3] + " " + w[4] + " " + w[5]);
    assertThat(hits(mvc, student, mode, w[4] + " " + w[5], 1)).hasSize(1);
  }

  /** 19.2, RET-1: a chunk of an unreadable document is never returned, even as the best match. */
  @ParameterizedTest
  @EnumSource(SearchMode.class)
  void aChunkOfAnUnreadableDocumentIsNeverReturnedEvenAsTheBestMatch(SearchMode mode) {
    String[] w = words(6);
    String query = w[0] + " " + w[1] + " " + w[2];
    UUID hidden = indexed("[]", query + " " + query);
    UUID visible = indexed(toStudent(), spread(w));

    assertThat(documentIds(hits(mvc, owner, mode, query, 6))).startsWith(hidden);
    List<UUID> found = documentIds(hits(mvc, student, mode, query, 6));
    assertThat(found).contains(visible).doesNotContain(hidden);
  }

  /**
   * 19.2, ACL-4: failed and archived documents are excluded, and so is one waiting to be processed
   * again, for everyone including the owner and admins, even when their chunks match best.
   */
  @ParameterizedTest
  @EnumSource(SearchMode.class)
  void failedArchivedAndUnindexedDocumentsAreExcludedEvenAsTheBestMatch(SearchMode mode) {
    String[] w = words(6);
    String query = w[0] + " " + w[1] + " " + w[2];
    UUID failed = document(toStudent(), query + " " + query);
    jdbc.sql("UPDATE document SET status = 'FAILED', failure_code = 'INTERNAL' WHERE id = :id")
        .param("id", failed)
        .update();
    UUID archived = indexed(toStudent(), query + " " + query);
    jdbc.sql("UPDATE document SET archived = true WHERE id = :id").param("id", archived).update();
    UUID uploaded = document(toStudent(), query + " " + query);
    UUID visible = indexed(toStudent(), spread(w));

    for (Session session : List.of(student, owner, TestAccounts.loginAsAdmin(mvc))) {
      assertThat(documentIds(hits(mvc, session, mode, query, 6)))
          .contains(visible)
          .doesNotContain(failed, archived, uploaded);
    }
  }

  /** 19.2, ACL-7: removing a grant removes the results on the next request. */
  @ParameterizedTest
  @EnumSource(SearchMode.class)
  void removingAGrantRemovesResultsOnTheNextRequest(SearchMode mode) {
    String[] w = words(2);
    String query = w[0] + " " + w[1];
    UUID id = indexed(toStudent(), query);
    assertThat(documentIds(hits(mvc, student, mode, query, 6))).contains(id);

    assertThat(
            TestDocuments.send(
                mvc,
                owner,
                HttpMethod.PUT,
                "/api/v1/documents/" + id + "/grants",
                "{\"grants\": []}"))
        .hasStatusOk();

    assertThat(documentIds(hits(mvc, student, mode, query, 6))).doesNotContain(id);
  }

  /**
   * 19.2, RET-4: with a very selective permission filter, k results still come back. The 120 chunks
   * the student may not see all match better than the six they may, so a filter applied after
   * {@code LIMIT}, or an approximate index, would leave the student with fewer or none.
   */
  @ParameterizedTest
  @EnumSource(SearchMode.class)
  void aVerySelectivePermissionFilterStillReturnsKResults(SearchMode mode) {
    String[] w = words(6);
    String query = w[0] + " " + w[1] + " " + w[2];
    List<String> best = new ArrayList<>();
    for (int i = 0; i < 120; i++) {
      best.add(query + " " + query);
    }
    indexed("[]", best.toArray(String[]::new));
    List<String> weaker = new ArrayList<>();
    for (int i = 0; i < 6; i++) {
      weaker.add(spread(w) + " " + words(1)[0]);
    }
    UUID visible = indexed(toStudent(), weaker.toArray(String[]::new));

    assertThat(documentIds(hits(mvc, student, mode, query, 6))).hasSize(6).containsOnly(visible);
    assertThat(documentIds(hits(mvc, student, mode, query, 4))).hasSize(4).containsOnly(visible);
  }

  /**
   * 19.2, RET-8, ACL-8: an unreadable id in the scope gives 404, with the same body as an id that
   * does not exist, and it is never silently dropped from a list of ids.
   */
  @ParameterizedTest
  @EnumSource(SearchMode.class)
  void anUnreadableIdInTheScopeAnswers404LikeAMissingOne(SearchMode mode) {
    String[] w = words(2);
    String query = w[0] + " " + w[1];
    UUID hidden = indexed("[]", query);
    UUID visible = indexed(toStudent(), query);

    MvcTestResult unreadable = search(mvc, student, mode, query, 6, List.of(hidden));
    MvcTestResult missing = search(mvc, student, mode, query, 6, List.of(UUID.randomUUID()));
    MvcTestResult mixed = search(mvc, student, mode, query, 6, List.of(visible, hidden));

    for (MvcTestResult result : List.of(unreadable, missing, mixed)) {
      assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }
    assertThat(problem(unreadable)).isEqualTo(problem(missing));
    assertThat(problem(mixed)).isEqualTo(problem(missing));
    assertThat(model.requests()).isEmpty();
  }

  /** RET-8: a readable id that is archived or not indexed is accepted and contributes nothing. */
  @ParameterizedTest
  @EnumSource(SearchMode.class)
  void aReadableIdThatIsNotRetrievableIsAcceptedAndContributesNothing(SearchMode mode) {
    String[] w = words(2);
    String query = w[0] + " " + w[1];
    UUID archived = indexed(toStudent(), query);
    jdbc.sql("UPDATE document SET archived = true WHERE id = :id").param("id", archived).update();
    UUID uploaded = document(toStudent(), query);
    UUID visible = indexed(toStudent(), query);
    indexed(toStudent(), query);

    assertThat(hits(search(mvc, student, mode, query, 6, List.of(archived, uploaded)))).isEmpty();
    assertThat(documentIds(hits(search(mvc, student, mode, query, 6, List.of(archived, visible)))))
        .containsExactly(visible);
  }

  /** Section 10: the category filter narrows the results. */
  @ParameterizedTest
  @EnumSource(SearchMode.class)
  void theCategoryFilterNarrowsTheResults(SearchMode mode) {
    String[] w = words(2);
    String query = w[0] + " " + w[1];
    UUID handbook = indexed(toStudent(), query);
    jdbc.sql("UPDATE document SET category = 'HANDBOOK' WHERE id = :id")
        .param("id", handbook)
        .update();
    UUID other = indexed(toStudent(), query);

    assertThat(documentIds(hits(mvc, student, mode, query, 6))).contains(handbook, other);
    MvcTestResult result =
        TestDocuments.send(
            mvc,
            student,
            HttpMethod.POST,
            "/api/v1/search",
            """
            {"mode": "%s", "query": "%s", "category": "HANDBOOK"}
            """
                .formatted(mode, query));
    assertThat(documentIds(hits(result))).containsExactly(handbook);
  }

  /** RET-9: without {@code k}, a search returns {@code app.retrieval.k} results, best first. */
  @ParameterizedTest
  @EnumSource(SearchMode.class)
  void withoutKASearchReturnsTheDefaultNumberBestFirst(SearchMode mode) {
    String[] w = words(1);
    List<String> chunks = new ArrayList<>();
    for (int i = 0; i < 8; i++) {
      chunks.add((w[0] + " ").repeat(i + 1) + words(1)[0]);
    }
    indexed(toStudent(), chunks.toArray(String[]::new));

    List<Hit> found = hits(mvc, student, mode, w[0], null);

    assertThat(found).hasSize(6);
    assertThat(found)
        .extracting(Hit::score)
        .isSortedAccordingTo((a, b) -> Double.compare(b, a))
        .allSatisfy(score -> assertThat(score).isPositive());
  }

  /**
   * RET-2, ADR-033: semantic search sends the query once, with the model's instruction prefix and
   * without surrounding space, and no transaction is open during the call (4.5, S14).
   */
  @Test
  void semanticSearchEmbedsTheQueryWithItsPrefixOutsideATransaction() {
    String[] w = words(2);
    indexed(toStudent(), w[0] + " " + w[1]);
    model.reset();

    hits(mvc, student, SearchMode.SEMANTIC, "  " + w[0] + " " + w[1] + "  ", 6);

    assertThat(embedding.queryPrefix()).isNotBlank();
    assertThat(model.requests())
        .containsExactly(List.of(embedding.queryPrefix() + w[0] + " " + w[1]));
    assertThat(model.callsInTransaction()).isZero();
  }

  /** RET-5: keyword search never calls the model. */
  @Test
  void keywordSearchNeverCallsTheModel() {
    String[] w = words(1);
    UUID id = indexed(toStudent(), w[0]);
    model.reset();

    assertThat(documentIds(hits(mvc, student, SearchMode.KEYWORD, w[0], 6))).contains(id);
    assertThat(model.requests()).isEmpty();
  }

  /** 15.1: when the model fails, semantic search answers 503 and keyword search still works. */
  @Test
  void aModelFailureAnswers503AndKeywordSearchStillWorks() {
    String[] w = words(1);
    UUID id = indexed(toStudent(), w[0]);
    model.failNext(() -> new IllegalStateException("model down"), 1);

    MvcTestResult result = search(mvc, student, SearchMode.SEMANTIC, w[0], 6, null);

    assertThat(result).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MODEL_UNAVAILABLE");
    assertThat(TestAccounts.body(result)).doesNotContain("model down");
    assertThat(documentIds(hits(mvc, student, SearchMode.KEYWORD, w[0], 6))).contains(id);
  }

  /** 15.1: a query of stop words only finds nothing in keyword mode, which is a normal answer. */
  @Test
  void aQueryOfStopWordsOnlyFindsNothingInKeywordMode() {
    indexed(toStudent(), "the rules of the institute and of its departments");

    assertThat(hits(mvc, student, SearchMode.KEYWORD, "the of and", 6)).isEmpty();
  }

  /**
   * Keyword results mark the words that matched, stemmed as PostgreSQL stems them, by position in
   * the text; semantic results mark nothing.
   */
  @Test
  void keywordResultsMarkTheMatchedWords() {
    String w = words(1)[0];
    String text = "Students must submit the " + w + " assignment before the deadline.";
    indexed(toStudent(), text);

    MvcTestResult keyword = search(mvc, student, SearchMode.KEYWORD, w + " deadlines", 1, null);
    List<Map<String, Integer>> highlights =
        JsonPath.read(TestAccounts.body(keyword), "$.results[0].highlights");
    assertThat(highlights)
        .extracting(h -> text.substring(h.get("start"), h.get("end")))
        .containsExactly(w, "deadline");

    MvcTestResult semantic = search(mvc, student, SearchMode.SEMANTIC, w + " deadlines", 1, null);
    assertThat(semantic).bodyJson().extractingPath("$.results[0].text").isEqualTo(text);
    assertThat(semantic).bodyJson().extractingPath("$.results[0].highlights").asArray().isEmpty();
  }

  /** 15.1: invalid requests answer 400 before any search or model call; no session answers 401. */
  @Test
  void invalidRequestsAreRejected() {
    List<String> invalid =
        List.of(
            "{\"mode\": \"SEMANTIC\", \"query\": \"   \"}",
            "{\"mode\": \"SEMANTIC\"}",
            "{\"query\": \"rules\"}",
            "{\"mode\": \"FUZZY\", \"query\": \"rules\"}",
            "{\"mode\": \"SEMANTIC\", \"query\": \"rules\", \"k\": 0}",
            "{\"mode\": \"SEMANTIC\", \"query\": \"rules\", \"k\": 51}",
            "{\"mode\": \"SEMANTIC\", \"query\": \"" + "a".repeat(1001) + "\"}",
            "{\"mode\": \"SEMANTIC\", \"query\": \"rules\\u0000\"}",
            "{\"mode\": \"SEMANTIC\", \"query\": \"rules\", \"documentIds\": [null]}",
            "{\"mode\": \"SEMANTIC\", \"query\": \"rules\", \"documentIds\": "
                + tooManyIds()
                + "}");
    for (String json : invalid) {
      assertThat(TestDocuments.send(mvc, student, HttpMethod.POST, "/api/v1/search", json))
          .as(json)
          .hasStatus(HttpStatus.BAD_REQUEST);
    }
    assertThat(model.requests()).isEmpty();
    assertThat(
            mvc.post()
                .uri("/api/v1/search")
                .contentType("application/json")
                .content("{\"mode\": \"KEYWORD\", \"query\": \"rules\"}")
                .exchange())
        .hasStatus(HttpStatus.UNAUTHORIZED);
  }

  /** A grant of the document to the test's student. */
  private String toStudent() {
    return "[{\"type\": \"USER\", \"userId\": \"%s\"}]".formatted(studentAccount.id());
  }

  /** An uploaded document of the owner with these grants and these chunks on pages 1, 2, ... */
  private UUID document(String grantsJson, String... chunks) {
    UUID id = TestDocuments.uploaded(mvc, owner, "Retrieval " + UUID.randomUUID(), grantsJson);
    for (int i = 0; i < chunks.length; i++) {
      AccessMatrix.insertChunk(jdbc, id, i, i + 1, chunks[i]);
    }
    return id;
  }

  /** The same, indexed. */
  private UUID indexed(String grantsJson, String... chunks) {
    UUID id = document(grantsJson, chunks);
    jdbc.sql("UPDATE document SET status = 'INDEXED', indexed_at = now() WHERE id = :id")
        .param("id", id)
        .update();
    return id;
  }

  /**
   * Words that no other chunk of the shared database contains: letters only, so that PostgreSQL's
   * parser and stemmer leave them whole.
   */
  private static String[] words(int count) {
    String[] words = new String[count];
    for (int i = 0; i < count; i++) {
      StringBuilder word = new StringBuilder("w");
      for (char c : UUID.randomUUID().toString().replace("-", "").substring(0, 12).toCharArray()) {
        word.append((char) (Character.isDigit(c) ? 'g' + (c - '0') : c));
      }
      words[i] = word.toString();
    }
    return words;
  }

  /**
   * The first three words with the other three between them: it contains every query word, so both
   * modes find it, but matches less well than the query itself.
   */
  private static String spread(String[] w) {
    return w[0] + " " + w[3] + " " + w[1] + " " + w[4] + " " + w[2] + " " + w[5];
  }

  private static Map<String, Object> problem(MvcTestResult result) {
    Map<String, Object> body = JsonPath.read(TestAccounts.body(result), "$");
    body.remove("instance");
    return body;
  }

  private static String tooManyIds() {
    List<String> ids = new ArrayList<>();
    for (int i = 0; i < 101; i++) {
      ids.add("\"" + UUID.randomUUID() + "\"");
    }
    return "[" + String.join(",", ids) + "]";
  }
}
