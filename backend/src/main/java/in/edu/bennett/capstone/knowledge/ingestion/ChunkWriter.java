package in.edu.bennett.capstone.knowledge.ingestion;

import in.edu.bennett.capstone.knowledge.ingestion.Chunker.Chunk;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes {@code document_chunk}, which only ingestion writes (4.3). Vectors are sent as pgvector's
 * text form through a bound parameter, never concatenated into SQL (SEC-7), so no extra JDBC type
 * library is needed.
 */
@Component
class ChunkWriter {

  private final JdbcTemplate jdbc;
  private final Clock clock;

  ChunkWriter(JdbcTemplate jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  /** A chunk with its vector. */
  record EmbeddedChunk(Chunk chunk, float[] embedding) {}

  /**
   * ING-3: replaces all chunks of a document, inside the caller's transaction, so readers see the
   * old set or the new one and never a mix.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  void replace(UUID documentId, List<EmbeddedChunk> chunks) {
    jdbc.update("DELETE FROM document_chunk WHERE document_id = ?", documentId);
    Timestamp now = Timestamp.from(clock.instant());
    jdbc.batchUpdate(
        """
        INSERT INTO document_chunk (document_id, chunk_index, page_start, page_end, content,
            token_count, embedding, created_at)
        VALUES (?, ?, ?, ?, ?, ?, CAST(? AS vector), ?)
        """,
        new BatchPreparedStatementSetter() {
          @Override
          public void setValues(PreparedStatement statement, int i) throws SQLException {
            EmbeddedChunk embedded = chunks.get(i);
            Chunk chunk = embedded.chunk();
            statement.setObject(1, documentId);
            statement.setInt(2, chunk.index());
            statement.setInt(3, chunk.pageStart());
            statement.setInt(4, chunk.pageEnd());
            statement.setString(5, chunk.content());
            statement.setInt(6, chunk.tokenCount());
            statement.setString(7, vectorText(embedded.embedding()));
            statement.setTimestamp(8, now);
          }

          @Override
          public int getBatchSize() {
            return chunks.size();
          }
        });
  }

  /** pgvector's text form, for example {@code [0.1,-0.25,3.0E-5]}. */
  static String vectorText(float[] vector) {
    StringBuilder text = new StringBuilder(vector.length * 12).append('[');
    for (int i = 0; i < vector.length; i++) {
      if (i > 0) {
        text.append(',');
      }
      text.append(vector[i]);
    }
    return text.append(']').toString();
  }
}
