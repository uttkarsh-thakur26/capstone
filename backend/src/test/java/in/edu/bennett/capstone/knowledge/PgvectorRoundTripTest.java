package in.edu.bennett.capstone.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Phase 0 exit criterion: the production database image, migrated by the real Flyway migrations,
 * stores a vector and returns rows ordered by cosine distance.
 *
 * <p>The table is a temporary one created by the test, so no schema is committed before the
 * embedding dimension (D6) is decided. {@code @JdbcTest} runs each test in one transaction, so the
 * temporary table lives on the same connection as the queries.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class PgvectorRoundTripTest {

  @Autowired JdbcClient jdbc;

  @Test
  void firstMigrationEnablesPgvectorOnPostgresql18() {
    String migration =
        jdbc.sql("SELECT description FROM flyway_schema_history WHERE version = '1' AND success")
            .query(String.class)
            .single();
    String pgvectorVersion =
        jdbc.sql("SELECT extversion FROM pg_extension WHERE extname = 'vector'")
            .query(String.class)
            .single();
    String serverVersion = jdbc.sql("SHOW server_version").query(String.class).single();

    assertThat(migration).isEqualTo("enable pgvector");
    assertThat(pgvectorVersion).isEqualTo("0.8.7");
    assertThat(serverVersion).startsWith("18.");
  }

  @Test
  void storesVectorsAndReadsThemBackOrderedByCosineDistance() {
    jdbc.sql("CREATE TEMP TABLE probe (id int PRIMARY KEY, embedding vector(3) NOT NULL)").update();
    insert(1, "[1,0,0]");
    insert(2, "[0,1,0]");
    insert(3, "[0.7,0.7,0]");

    // The query vector is a bound parameter, never concatenated into the SQL (SEC-7).
    List<Long> nearestFirst =
        jdbc.sql(
                """
                SELECT id FROM probe
                ORDER BY embedding <=> CAST(:query AS vector)
                LIMIT 3
                """)
            .param("query", "[1,0.1,0]")
            .query(Long.class)
            .list();
    Double closestDistance =
        jdbc.sql("SELECT embedding <=> CAST(:query AS vector) FROM probe WHERE id = 1")
            .param("query", "[1,0.1,0]")
            .query(Double.class)
            .single();
    String storedVector =
        jdbc.sql("SELECT embedding::text FROM probe WHERE id = 3").query(String.class).single();

    assertThat(nearestFirst).containsExactly(1L, 3L, 2L);
    // 1 - cos(angle between [1,0,0] and [1,0.1,0]) = 1 - 1/sqrt(1.01)
    assertThat(closestDistance).isCloseTo(1 - 1 / Math.sqrt(1.01), within(1e-6));
    assertThat(storedVector).isEqualTo("[0.7,0.7,0]");
  }

  private void insert(int id, String vector) {
    jdbc.sql("INSERT INTO probe (id, embedding) VALUES (:id, CAST(:embedding AS vector))")
        .param("id", id)
        .param("embedding", vector)
        .update();
  }
}
