package in.edu.bennett.capstone.knowledge.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import in.edu.bennett.capstone.knowledge.FakeEmbeddingModel;
import in.edu.bennett.capstone.knowledge.TestAccounts;
import in.edu.bennett.capstone.knowledge.TestAccounts.Session;
import in.edu.bennett.capstone.knowledge.TestPdfs;
import in.edu.bennett.capstone.knowledge.TestcontainersConfiguration;
import in.edu.bennett.capstone.knowledge.document.TestDocuments;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * The worker as it runs in the application: woken after an upload commits, processing at most
 * {@code parallel-documents} at once (ING-12), and recovering a run whose worker died (ING-2). The
 * poll interval is an hour, so only the wake-up can start work quickly. The context is closed
 * afterwards so its scheduler stops.
 */
@SpringBootTest(
    properties = {
      "app.ingestion.enabled=true",
      "app.ingestion.poll-interval=1h",
      "app.ingestion.heartbeat-interval=1s",
      "app.ingestion.recovery-interval=1s",
      "app.ingestion.stuck-timeout=4s",
      "app.ingestion.parallel-documents=2"
    })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@DirtiesContext
class IngestionWorkerTest {

  @Autowired MockMvcTester mvc;

  @Autowired JdbcClient jdbc;

  @Autowired FakeEmbeddingModel model;

  @Autowired IngestionWorker worker;

  @AfterEach
  void resetTheModel() {
    model.reset();
  }

  @Test
  void uploadsAreIndexedRightAwayAndAtMostTwoAtATime() {
    Session faculty = TestAccounts.login(mvc, TestAccounts.registerAs(mvc, jdbc, "FACULTY"));
    model.delayEachCall(300);
    List<UUID> ids = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      ids.add(
          upload(
              faculty, TestPdfs.withPageLines(List.of(List.of("Notice " + i + " about fees.")))));
    }

    awaitUntil(
        Duration.ofSeconds(30), () -> ids.stream().allMatch(id -> "INDEXED".equals(status(id))));

    assertThat(model.maxConcurrentCalls()).isEqualTo(2);
  }

  /** A claim whose worker never comes back is recovered and then indexed by this worker. */
  @Test
  void aRunWhoseWorkerDiedIsRecoveredAndIndexed() {
    Session faculty = TestAccounts.login(mvc, TestAccounts.registerAs(mvc, jdbc, "FACULTY"));
    UUID id = upload(faculty, TestPdfs.withPageLines(List.of(List.of("Notice about rooms."))));
    awaitUntil(Duration.ofSeconds(10), () -> "INDEXED".equals(status(id)));
    // Another instance claimed it again and was killed: PROCESSING with a heartbeat nobody renews.
    jdbc.sql(
            """
            UPDATE document SET status = 'PROCESSING', processing_run_id = gen_random_uuid(),
                processing_heartbeat_at = now(), processing_started_at = now() WHERE id = :id
            """)
        .param("id", id)
        .update();

    // Recovery returns it once the heartbeat is older than the stuck timeout, counting one attempt.
    awaitUntil(Duration.ofSeconds(20), () -> attempts(id) == 1);
    assertThat(status(id)).isEqualTo("UPLOADED");
    // The next poll picks it up; its timer is an hour away here, so the test runs it.
    worker.poll();

    awaitUntil(Duration.ofSeconds(10), () -> "INDEXED".equals(status(id)));
  }

  private UUID upload(Session session, byte[] pdf) {
    var result =
        TestDocuments.upload(
            mvc, session, pdf, "notice.pdf", TestDocuments.metadata("Notice", "[]"));
    assertThat(result).hasStatus(org.springframework.http.HttpStatus.CREATED);
    return UUID.fromString(JsonPath.read(TestAccounts.body(result), "$.id"));
  }

  private String status(UUID id) {
    return jdbc.sql("SELECT status FROM document WHERE id = :id")
        .param("id", id)
        .query(String.class)
        .single();
  }

  private int attempts(UUID id) {
    return jdbc.sql("SELECT processing_attempts FROM document WHERE id = :id")
        .param("id", id)
        .query(Integer.class)
        .single();
  }

  private static void awaitUntil(Duration timeout, BooleanSupplier condition) {
    Instant deadline = Instant.now().plus(timeout);
    while (!condition.getAsBoolean()) {
      if (Instant.now().isAfter(deadline)) {
        throw new AssertionError("Condition not met within " + timeout);
      }
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError(e);
      }
    }
  }
}
