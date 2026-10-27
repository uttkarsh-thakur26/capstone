package in.edu.bennett.capstone.knowledge.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import in.edu.bennett.capstone.knowledge.FakeEmbeddingModel;
import in.edu.bennett.capstone.knowledge.TestAccounts;
import in.edu.bennett.capstone.knowledge.TestAccounts.Session;
import in.edu.bennett.capstone.knowledge.TestcontainersConfiguration;
import in.edu.bennett.capstone.knowledge.document.DocumentProcessing;
import in.edu.bennett.capstone.knowledge.document.ProcessingRun;
import in.edu.bennett.capstone.knowledge.document.TestDocuments;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Ingestion against PostgreSQL, with the worker off and the pipeline driven by the test. The queue
 * is the whole {@code document} table, so these classes use a context and a database of their own
 * (the properties make the context distinct) and empty the table before each test. Small limits let
 * the tests reach them with small files: two chunks per embedding request, six pages, ten chunks.
 */
@SpringBootTest(
    properties = {
      "app.ingestion.embedding-batch-size=2",
      "app.ingestion.max-pages=6",
      "app.ingestion.max-chunks=10"
    })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
abstract class IngestionTestSupport {

  @Autowired MockMvcTester mvc;

  @Autowired JdbcClient jdbc;

  @Autowired DocumentProcessing processing;

  @Autowired IngestionPipeline pipeline;

  @Autowired FakeEmbeddingModel model;

  Session faculty;

  @BeforeEach
  void emptyTheQueue() {
    jdbc.sql("DELETE FROM document").update();
    faculty = TestAccounts.login(mvc, TestAccounts.registerAs(mvc, jdbc, "FACULTY"));
  }

  @AfterEach
  void resetTheModel() {
    model.reset();
  }

  UUID upload(byte[] pdf) {
    MvcTestResult result =
        TestDocuments.upload(
            mvc, faculty, pdf, "fixture.pdf", TestDocuments.metadata("Fixture", "[]"));
    assertThat(result).hasStatus(HttpStatus.CREATED);
    return UUID.fromString(JsonPath.read(TestAccounts.body(result), "$.id"));
  }

  /**
   * Claims the next queued document and processes it in this thread, as one worker thread would.
   */
  IngestionPipeline.Outcome processNext() {
    ProcessingRun run = processing.claimNext().orElseThrow();
    return pipeline.process(run, RunControl.running());
  }

  Map<String, Object> document(UUID id) {
    return jdbc.sql(
            """
            SELECT status, failure_code, failure_message, page_count, chunk_count, embedding_model,
                embedding_dim, processing_attempts, processing_run_id
            FROM document WHERE id = :id
            """)
        .param("id", id)
        .query()
        .singleRow();
  }

  String status(UUID id) {
    return (String) document(id).get("status");
  }

  /** A chunk row as stored. */
  record StoredChunk(long id, int index, int pageStart, int pageEnd, String content, int tokens) {}

  List<StoredChunk> chunks(UUID documentId) {
    return jdbc.sql(
            """
            SELECT id, chunk_index, page_start, page_end, content, token_count FROM document_chunk
            WHERE document_id = :id ORDER BY chunk_index
            """)
        .param("id", documentId)
        .query(
            (rs, row) ->
                new StoredChunk(
                    rs.getLong(1),
                    rs.getInt(2),
                    rs.getInt(3),
                    rs.getInt(4),
                    rs.getString(5),
                    rs.getInt(6)))
        .list();
  }

  /**
   * The chunks of a document that the retrievable predicate of 5.3 lets through, by status (ACL-4).
   * Phase 4's search uses the same condition.
   */
  long retrievableChunks(UUID documentId) {
    return jdbc.sql(
            """
            SELECT count(*) FROM document_chunk c JOIN document d ON d.id = c.document_id
            WHERE d.id = :id AND d.status = 'INDEXED' AND d.archived = false
            """)
        .param("id", documentId)
        .query(Long.class)
        .single();
  }

  /** {@code lines} lines of one marker word plus filler words, about 12 tokens each. */
  static List<String> lines(String marker, int lines) {
    return java.util.stream.IntStream.range(0, lines)
        .mapToObj(i -> marker + " rule " + i + " applies to every student enrolled this term.")
        .toList();
  }

  void reprocess(UUID id) {
    assertThat(
            TestDocuments.send(
                mvc,
                faculty,
                org.springframework.http.HttpMethod.POST,
                "/api/v1/documents/" + id + "/reprocess",
                null))
        .hasStatusOk();
  }
}
