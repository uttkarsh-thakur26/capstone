package in.edu.bennett.capstone.knowledge.retrieval;

import static in.edu.bennett.capstone.knowledge.retrieval.TestSearch.hits;
import static in.edu.bennett.capstone.knowledge.retrieval.TestSearch.search;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import in.edu.bennett.capstone.knowledge.FakeChatModel;
import in.edu.bennett.capstone.knowledge.IntegrationTest;
import in.edu.bennett.capstone.knowledge.TestAccounts;
import in.edu.bennett.capstone.knowledge.TestAccounts.Session;
import in.edu.bennett.capstone.knowledge.conversation.TestConversations;
import in.edu.bennett.capstone.knowledge.conversation.TestConversations.Reply;
import in.edu.bennett.capstone.knowledge.document.AccessMatrix;
import in.edu.bennett.capstone.knowledge.document.TestDocuments;
import in.edu.bennett.capstone.knowledge.retrieval.TestSearch.Hit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Hybrid search (RET-6, ADR-040): Reciprocal Rank Fusion of the semantic and keyword rankings, the
 * keyword ranking needing any word of the query, and the evidence gate on cosine similarity. The
 * access rules of hybrid mode are in {@code SearchAccessMatrixTest} and {@code RetrievalTest},
 * which run every mode.
 *
 * <p>Every search is scoped to the test's own document, so other chunks of the shared database
 * cannot change a ranking. The fake embedding model puts the query prefix's words in the query's
 * vector, so the stop words used here are ones the prefix does not contain ("of", "and").
 */
@IntegrationTest
class HybridSearchTest {

  @Autowired MockMvcTester mvc;

  @Autowired JdbcClient jdbc;

  @Autowired FakeChatModel chat;

  private Session faculty;

  @BeforeEach
  void account() {
    faculty = TestAccounts.login(mvc, TestAccounts.registerAs(mvc, jdbc, "FACULTY"));
  }

  /**
   * The fake chat model is shared by every test class of the context; it is emptied before and
   * after each test, so calls made by another class never count here, whatever the class order.
   */
  @BeforeEach
  @AfterEach
  void resetTheModel() {
    chat.reset();
  }

  /**
   * The stop words make chunk A the closest in meaning to the query under the fake model, but full
   * text search ignores them, so only B is in the keyword ranking. Fused, B scores {@code 1/62 +
   * 1/61} and A only {@code 1/61}: B comes first, although semantic mode puts A first.
   */
  @Test
  void aChunkInBothRankingsOutranksAChunkInOneOnly() {
    String a = "of and of and of and";
    String b = "electrode calibration rules apply here today";
    String c = "unrelated text about something else entirely";
    UUID id = indexed(a, b, c);
    String query = "electrode calibration of and";

    List<Hit> semantic = hits(search(mvc, faculty, SearchMode.SEMANTIC, query, 3, List.of(id)));
    List<Hit> keyword = hits(search(mvc, faculty, SearchMode.KEYWORD, query, 3, List.of(id)));
    List<Hit> hybrid = hits(search(mvc, faculty, SearchMode.HYBRID, query, 3, List.of(id)));

    assertThat(semantic).extracting(Hit::text).startsWith(a, b);
    assertThat(keyword).extracting(Hit::text).containsExactly(b);
    assertThat(hybrid).extracting(Hit::text).startsWith(b, a);
    double first =
        1.0 / (SqlChunkSearch.RRF_CONSTANT + 2) + 1.0 / (SqlChunkSearch.RRF_CONSTANT + 1);
    assertThat(hybrid.getFirst().score())
        .isCloseTo(first, org.assertj.core.data.Offset.offset(1e-9));
    assertThat(hybrid.get(1).score())
        .isCloseTo(
            1.0 / (SqlChunkSearch.RRF_CONSTANT + 1), org.assertj.core.data.Offset.offset(1e-9));
  }

  /**
   * ADR-040: keyword mode needs every word, so a query with one word that no chunk holds finds
   * nothing there; the keyword ranking inside hybrid search needs any word, finds the chunk and
   * marks the word that matched. Each hybrid result carries the same similarity semantic mode
   * reports for it.
   */
  @Test
  void theKeywordRankingOfHybridSearchNeedsAnyWordAndResultsCarryTheirSimilarity() {
    String text = "calibration of the meters happens every semester";
    UUID id = indexed(text);
    String query = "calibration wqzxnonexistent";

    assertThat(hits(search(mvc, faculty, SearchMode.KEYWORD, query, 6, List.of(id)))).isEmpty();
    MvcTestResult hybrid = search(mvc, faculty, SearchMode.HYBRID, query, 6, List.of(id));
    MvcTestResult semantic = search(mvc, faculty, SearchMode.SEMANTIC, query, 6, List.of(id));

    String body = TestAccounts.body(hybrid);
    assertThat((String) JsonPath.read(body, "$.mode")).isEqualTo("HYBRID");
    assertThat((String) JsonPath.read(body, "$.results[0].text")).isEqualTo(text);
    int start = JsonPath.read(body, "$.results[0].highlights[0].start");
    int end = JsonPath.read(body, "$.results[0].highlights[0].end");
    assertThat(text.substring(start, end)).isEqualTo("calibration");
    double similarity = ((Number) JsonPath.read(body, "$.results[0].similarity")).doubleValue();
    assertThat(similarity).isEqualTo(hits(semantic).getFirst().score());
    assertThat((Object) JsonPath.read(TestAccounts.body(semantic), "$.results[0].similarity"))
        .isNotNull();
  }

  /**
   * ADR-040: inside hybrid search, a chunk that holds every word of the query ranks above one that
   * holds only some of them, however often it repeats them. Here R repeats "calibration" and is
   * also the closest by meaning, so ranked by any word it would win both rankings; with every word
   * first, A is first by keywords and R second, and the fused scores tie.
   */
  @Test
  void aChunkWithEveryWordOutranksOneThatRepeatsASingleWord() {
    String all = "electrode calibration rules apply here today";
    String repeats = "calibration calibration calibration calibration calibration of and";
    UUID id = indexed(all, repeats);
    String query = "electrode calibration of and";

    List<Hit> semantic = hits(search(mvc, faculty, SearchMode.SEMANTIC, query, 2, List.of(id)));
    List<Hit> hybrid = hits(search(mvc, faculty, SearchMode.HYBRID, query, 2, List.of(id)));

    assertThat(semantic).extracting(Hit::text).containsExactly(repeats, all);
    // A: second by meaning, first by keywords; R: first by meaning, second by keywords. A tie,
    // which the keyword order breaks.
    assertThat(hybrid).extracting(Hit::text).containsExactly(all, repeats);
    assertThat(hybrid.get(0).score()).isEqualTo(hybrid.get(1).score());
  }

  /** Keyword mode embeds nothing, so its results report no similarity. */
  @Test
  void keywordResultsReportNoSimilarity() {
    UUID id = indexed("calibration of the meters happens every semester");

    MvcTestResult keyword = search(mvc, faculty, SearchMode.KEYWORD, "calibration", 6, List.of(id));

    assertThat(keyword).bodyJson().extractingPath("$.results[0].similarity").isNull();
  }

  /**
   * ADR-040: the evidence gate compares the best cosine similarity of the chunks retrieved with the
   * floor, never the fused score. A long chunk sharing one word with the question is in the hybrid
   * results, through the keyword ranking, but is not similar enough: the outcome is insufficient
   * evidence and the chat model is not called. A question close to a chunk is answered.
   */
  @Test
  void theEvidenceGateUsesSimilarityInHybridMode() {
    StringBuilder longChunk = new StringBuilder("calibration");
    for (int i = 0; i < 30; i++) {
      longChunk.append(" filler").append((char) ('a' + i % 26)).append((char) ('a' + i / 26));
    }
    UUID id = indexed(longChunk.toString(), "meters are checked every semester");
    String question = "calibration procedure schedule";

    assertThat(hits(search(mvc, faculty, SearchMode.HYBRID, question, 6, List.of(id))))
        .extracting(Hit::text)
        .contains(longChunk.toString());
    Reply gated =
        TestConversations.reply(
            TestConversations.ask(
                mvc, faculty, TestConversations.create(mvc, faculty), question, List.of(id)));
    assertThat(gated.outcome()).isEqualTo("INSUFFICIENT_EVIDENCE");
    assertThat(chat.calls()).isZero();

    Reply answered =
        TestConversations.reply(
            TestConversations.ask(
                mvc,
                faculty,
                TestConversations.create(mvc, faculty),
                "meters are checked every semester",
                List.of(id)));
    assertThat(answered.outcome()).isEqualTo("ANSWERED");
    assertThat(chat.calls()).isOne();
  }

  /**
   * 11.1 step 4: questions retrieve in hybrid mode by default. Six chunks of stop words and one
   * filler are closer in meaning to the question than K, which shares only "electrode" with it and
   * is seventh by meaning, so semantic retrieval of six chunks would leave K out. The keyword
   * ranking puts K first, the fusion keeps it, and K's text reaches the prompt.
   */
  @Test
  void questionsRetrieveWithHybridSearchByDefault() {
    String[] chunks = new String[7];
    for (int i = 0; i < 6; i++) {
      chunks[i] = "of and of and of and filler" + (char) ('a' + i);
    }
    String k = "electrode fillerp fillerq fillerr fillers fillert filleru fillerv fillerw";
    chunks[6] = k;
    UUID id = indexed(chunks);
    String question = "electrode of and";
    assertThat(hits(search(mvc, faculty, SearchMode.SEMANTIC, question, 6, List.of(id))))
        .extracting(Hit::text)
        .doesNotContain(k);

    Reply reply =
        TestConversations.reply(
            TestConversations.ask(
                mvc, faculty, TestConversations.create(mvc, faculty), question, List.of(id)));

    assertThat(reply.outcome()).isEqualTo("ANSWERED");
    assertThat(chat.requests().getLast().user()).contains(k);
  }

  /** {@code anyWords}: letters and digits only, joined by {@code or}, without the word "or". */
  @Test
  void theQueryWordsAreJoinedByOr() {
    assertThat(SearchService.anyWords("module 1 java")).isEqualTo("module or 1 or java");
    assertThat(SearchService.anyWords("\"exact phrase\" -not OR java"))
        .isEqualTo("exact or phrase or not or java");
    assertThat(SearchService.anyWords("  ?!  ")).isEmpty();
    assertThat(SearchService.anyWords("नियम 2026")).isEqualTo("नियम or 2026");
  }

  /** A query of no words at all finds nothing by keyword, and still ranks by meaning. */
  @Test
  void aQueryWithoutWordsStillRanksByMeaning() {
    UUID id = indexed("calibration of the meters happens every semester");

    List<Hit> hybrid = hits(search(mvc, faculty, SearchMode.HYBRID, "?!", 6, List.of(id)));

    assertThat(hybrid).hasSize(1);
  }

  /** A document of the test's account with these chunks, one per page, indexed. */
  private UUID indexed(String... chunks) {
    UUID id = TestDocuments.uploaded(mvc, faculty, "Hybrid " + UUID.randomUUID(), "[]");
    for (int i = 0; i < chunks.length; i++) {
      AccessMatrix.insertChunk(jdbc, id, i, i + 1, chunks[i]);
    }
    jdbc.sql("UPDATE document SET status = 'INDEXED', indexed_at = now() WHERE id = :id")
        .param("id", id)
        .update();
    return id;
  }
}
