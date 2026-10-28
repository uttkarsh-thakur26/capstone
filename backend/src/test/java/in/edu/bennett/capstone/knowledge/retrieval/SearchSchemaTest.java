package in.edu.bennett.capstone.knowledge.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import in.edu.bennett.capstone.knowledge.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * What search needs from the schema (specification 7.2, 7.4; RET-3, RET-5), checked in the
 * database.
 */
@IntegrationTest
class SearchSchemaTest {

  @Autowired JdbcClient jdbc;

  /** A stored generated column: PostgreSQL 18 would make it virtual by default, and unindexable. */
  @Test
  void contentTsvIsAStoredColumnGeneratedFromTheEnglishText() {
    assertThat(
            jdbc.sql(
                    """
                    SELECT attgenerated FROM pg_attribute
                    WHERE attrelid = 'document_chunk'::regclass AND attname = 'content_tsv'
                    """)
                .query(String.class)
                .single())
        .isEqualTo("s");
    assertThat(
            jdbc.sql(
                    "SELECT pg_get_expr(adbin, adrelid) FROM pg_attrdef"
                        + " WHERE adrelid = 'document_chunk'::regclass")
                .query(String.class)
                .list())
        .containsExactly("to_tsvector('english'::regconfig, content)");
  }

  @Test
  void contentTsvHasAGinIndexAndTheVectorsHaveNoIndex() {
    assertThat(
            jdbc.sql(
                    """
                    SELECT am.amname || ' ' || pg_get_indexdef(i.indexrelid)
                    FROM pg_index i
                    JOIN pg_class c ON c.oid = i.indexrelid
                    JOIN pg_am am ON am.oid = c.relam
                    WHERE i.indrelid = 'document_chunk'::regclass
                    ORDER BY c.relname
                    """)
                .query(String.class)
                .list())
        .hasSize(3)
        .anyMatch(index -> index.startsWith("gin ") && index.contains("(content_tsv)"))
        .noneMatch(index -> index.contains("embedding"));
  }
}
