package in.edu.bennett.capstone.knowledge.ingestion;

import in.edu.bennett.capstone.knowledge.ai.EmbeddingProperties;
import in.edu.bennett.capstone.knowledge.audit.AuditAction;
import in.edu.bennett.capstone.knowledge.audit.AuditEvent;
import in.edu.bennett.capstone.knowledge.audit.AuditOutcome;
import in.edu.bennett.capstone.knowledge.audit.AuditService;
import in.edu.bennett.capstone.knowledge.document.DocumentProcessing;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Startup checks that stop the application when stored vectors and settings disagree (ING-7, S11,
 * ADR-034). The model's own dimension is checked by {@code EmbeddingModelCheck}.
 *
 * <ul>
 *   <li>ING-7: a chunk, with the tokenizer margin, must fit the model's input, or its tail would be
 *       refused or cut.
 *   <li>S11: the column's dimension must equal the configured one. Flyway does not notice a changed
 *       placeholder on a database that is already migrated, so only the first insert would fail.
 *   <li>S11: every indexed document must have been embedded by the configured model. Another model
 *       with the same dimension would pass every other check and make searches compare unlike
 *       vectors.
 * </ul>
 */
@Component
class IndexCompatibilityCheck implements SmartInitializingSingleton {

  private static final Logger log = LoggerFactory.getLogger(IndexCompatibilityCheck.class);

  private final IngestionProperties ingestion;
  private final EmbeddingProperties embedding;
  private final JdbcClient jdbc;
  private final DocumentProcessing processing;
  private final AuditService audit;

  IndexCompatibilityCheck(
      IngestionProperties ingestion,
      EmbeddingProperties embedding,
      JdbcClient jdbc,
      DocumentProcessing processing,
      AuditService audit) {
    this.ingestion = ingestion;
    this.embedding = embedding;
    this.jdbc = jdbc;
    this.processing = processing;
    this.audit = audit;
  }

  @Override
  public void afterSingletonsInstantiated() {
    checkChunkSize();
    checkColumnDimension();
    checkIndexedModels();
  }

  void checkChunkSize() {
    double needed = ingestion.chunkSizeTokens() * embedding.tokenizerMargin();
    if (needed > embedding.maxInputTokens()) {
      throw new IllegalStateException(
          "app.ingestion.chunk-size-tokens is "
              + ingestion.chunkSizeTokens()
              + "; with the tokenizer margin of "
              + embedding.tokenizerMargin()
              + " a chunk may need "
              + Math.round(needed)
              + " model tokens, more than app.embedding.max-input-tokens ("
              + embedding.maxInputTokens()
              + ") (ING-7)");
    }
  }

  void checkColumnDimension() {
    String type =
        jdbc.sql(
                """
                SELECT format_type(a.atttypid, a.atttypmod) FROM pg_attribute a
                WHERE a.attrelid = 'document_chunk'::regclass AND a.attname = 'embedding'
                """)
            .query(String.class)
            .single();
    String expected = "vector(" + embedding.dimensions() + ")";
    if (!expected.equals(type)) {
      throw new IllegalStateException(
          "document_chunk.embedding is "
              + type
              + " but app.embedding.dimensions is "
              + embedding.dimensions()
              + ". Changing the dimension needs a migration and a full re-index (ING-9, S11)");
    }
  }

  void checkIndexedModels() {
    List<String> others =
        processing.indexedEmbeddingModels().stream()
            .filter(model -> !model.equals(embedding.model()))
            .toList();
    if (!others.isEmpty() && embedding.reindexOnModelChange()) {
      // ADR-041: asked for in the configuration, so the index is rebuilt with the new model.
      int queued = processing.requeueOtherModels(embedding.model());
      audit.record(
          AuditEvent.of(AuditAction.REINDEX_STARTED, AuditOutcome.SUCCESS, null, null)
              .withDetails(
                  Map.of(
                      "reason",
                      "model change",
                      "fromModels",
                      others,
                      "toModel",
                      embedding.model(),
                      "queued",
                      queued)));
      log.warn(
          "Documents were indexed with {}; sent {} back for processing with {}",
          others,
          queued,
          embedding.model());
      return;
    }
    if (!others.isEmpty()) {
      throw new IllegalStateException(
          "Documents are indexed with "
              + others
              + " but app.embedding.model is "
              + embedding.model()
              + ". Vectors of different models cannot be searched together; set the model back,"
              + " or start once with APP_EMBEDDING_REINDEX_ON_MODEL_CHANGE=true to re-index every"
              + " document with the new model (ING-9, S11, ADR-041)");
    }
  }
}
