package in.edu.bennett.capstone.knowledge.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import in.edu.bennett.capstone.knowledge.TestPdfs;
import in.edu.bennett.capstone.knowledge.ai.EmbeddingProperties;
import in.edu.bennett.capstone.knowledge.audit.AuditService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/** The startup checks of ING-7 and S11, against the migrated database (ADR-034). */
class IndexCompatibilityCheckTest extends IngestionTestSupport {

  @Autowired IngestionProperties ingestion;

  @Autowired EmbeddingProperties embedding;

  @Autowired ApplicationContext context;

  @Autowired AuditService audit;

  @Test
  void theRunningApplicationPassesEveryCheck() {
    assertThatNoException()
        .isThrownBy(() -> check(ingestion, embedding).afterSingletonsInstantiated());
    // The test profile replaces the model: exactly one embedding model, the fake one.
    assertThat(context.getBeansOfType(EmbeddingModel.class)).containsOnlyKeys("fakeEmbeddingModel");
  }

  /** 19.2: a chunk size above the embedding limit stops startup (ING-7). */
  @Test
  void aChunkSizeAboveTheEmbeddingLimitStopsStartup() {
    IngestionProperties tooLarge = withChunkSize(1500);

    assertThatIllegalStateException()
        .isThrownBy(() -> check(tooLarge, embedding).checkChunkSize())
        .withMessageContaining("2250")
        .withMessageContaining("ING-7");
  }

  /** S11: the configured dimension must be the column's, which Flyway does not re-check. */
  @Test
  void aDimensionThatDiffersFromTheColumnStopsStartup() {
    assertThatIllegalStateException()
        .isThrownBy(() -> check(ingestion, withDimensions(768)).checkColumnDimension())
        .withMessageContaining("vector(1024)")
        .withMessageContaining("768");
  }

  /** S11: documents indexed by another model of the same dimension stop startup. */
  @Test
  void documentsIndexedWithAnotherModelStopStartup() {
    upload(TestPdfs.withPageLines(List.of(lines("page1marker", 5))));
    assertThat(processNext()).isEqualTo(IngestionPipeline.Outcome.INDEXED);

    assertThatIllegalStateException()
        .isThrownBy(() -> check(ingestion, withModel("nomic-embed-text")).checkIndexedModels())
        .withMessageContaining("qwen3-embedding:0.6b")
        .withMessageContaining("nomic-embed-text");
  }

  /** ADR-041: with the setting on, the same documents are sent back to be embedded again. */
  @Test
  void withTheSettingOnStartupReindexesDocumentsOfAnotherModel() {
    UUID id = upload(TestPdfs.withPageLines(List.of(lines("modelchangemarker", 5))));
    assertThat(processNext()).isEqualTo(IngestionPipeline.Outcome.INDEXED);

    assertThatNoException()
        .isThrownBy(
            () -> check(ingestion, withModel("nomic-embed-text", true)).checkIndexedModels());

    assertThat(
            jdbc.sql("SELECT status FROM document WHERE id = :id")
                .param("id", id)
                .query(String.class)
                .single())
        .isEqualTo("UPLOADED");
    String details =
        jdbc.sql(
                "SELECT details::text FROM audit_log WHERE action = 'REINDEX_STARTED'"
                    + " AND actor_user_id IS NULL ORDER BY id DESC LIMIT 1")
            .query(String.class)
            .single();
    assertThat(details).contains("model change").contains("nomic-embed-text");
  }

  private IndexCompatibilityCheck check(
      IngestionProperties ingestion, EmbeddingProperties embedding) {
    return new IndexCompatibilityCheck(ingestion, embedding, jdbc, processing, audit);
  }

  private IngestionProperties withChunkSize(int size) {
    IngestionProperties p = ingestion;
    return new IngestionProperties(
        p.enabled(),
        size,
        p.chunkOverlapTokens(),
        p.embeddingBatchSize(),
        p.parallelDocuments(),
        p.pollInterval(),
        p.heartbeatInterval(),
        p.recoveryInterval(),
        p.stuckTimeout(),
        p.maxAttempts(),
        p.maxPages(),
        p.maxChunks(),
        p.minCharsPerPage(),
        p.shutdownWait());
  }

  private EmbeddingProperties withDimensions(int dimensions) {
    EmbeddingProperties e = embedding;
    return new EmbeddingProperties(
        e.model(),
        dimensions,
        e.maxInputTokens(),
        e.tokenizerMargin(),
        e.queryPrefix(),
        e.retry(),
        false);
  }

  private EmbeddingProperties withModel(String model) {
    return withModel(model, false);
  }

  private EmbeddingProperties withModel(String model, boolean reindex) {
    EmbeddingProperties e = embedding;
    return new EmbeddingProperties(
        model,
        e.dimensions(),
        e.maxInputTokens(),
        e.tokenizerMargin(),
        e.queryPrefix(),
        e.retry(),
        reindex);
  }
}
