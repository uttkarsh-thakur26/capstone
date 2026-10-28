package in.edu.bennett.capstone.knowledge.retrieval;

import in.edu.bennett.capstone.knowledge.common.PgVector;
import in.edu.bennett.capstone.knowledge.security.CurrentUser;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The retrieval SQL, in one class (4.3, ADR-001). Each search is a single statement that joins
 * {@code document_chunk} to {@code document} and checks {@code document_grant}, with the
 * retrievable predicate in its {@code WHERE} clause, so a chunk the user may not retrieve is never
 * read, ranked or counted, and {@code LIMIT} applies to the permitted chunks only (RET-1, RET-4,
 * ACL-E2). User id and role come from the authenticated principal (ACL-E4). Nothing is filtered in
 * Java afterwards.
 */
@Component
class SqlChunkSearch implements ChunkSearch {

  /**
   * The retrievable predicate of specification 5.3, the one definition for native SQL (ACL-E3): the
   * document is indexed and not archived (ACL-4), and the user can read it (ACL-1). Admins read
   * every document (D15). The JPA form of the readable part is {@code RoleAndGrantAccessPolicy};
   * the access-matrix tests check both.
   */
  private static final String RETRIEVABLE =
      """
      d.status = 'INDEXED'
        AND d.archived = false
        AND (:role = 'ADMIN'
             OR d.owner_id = :uid
             OR EXISTS (
                  SELECT 1 FROM document_grant g
                  WHERE g.document_id = d.id
                    AND ((g.grantee_type = 'ROLE' AND g.grantee_role = :role)
                      OR (g.grantee_type = 'USER' AND g.grantee_user_id = :uid))))\
      """;

  /**
   * RET-2, RET-3: exact search, since no vector index exists (D11). The order is the bare distance
   * so that an HNSW index, if one is ever added, could serve it; wrapping the distance in an
   * expression would rule the index out. Similarity is reported as {@code 1 - distance}.
   */
  private static final String SEMANTIC =
      """
      SELECT c.document_id, d.title, c.page_start, c.page_end, c.content,
             1 - (c.embedding <=> CAST(:embedding AS vector)) AS score
      FROM document_chunk c
      JOIN document d ON d.id = c.document_id
      WHERE %s%s
      ORDER BY c.embedding <=> CAST(:embedding AS vector)
      LIMIT :k
      """;

  /**
   * RET-5: full-text search on {@code content_tsv} with {@code websearch_to_tsquery}, ranked by
   * {@code ts_rank_cd}, in English (D13). The inner query picks the top {@code k}, so {@code
   * ts_headline}, which parses the text again, runs on those chunks only. Equal ranks keep the
   * order in which chunks were written.
   */
  private static final String KEYWORD =
      """
      SELECT r.document_id, r.title, r.page_start, r.page_end, r.content, r.score,
             ts_headline('english', r.content, r.query, :headline) AS marked
      FROM (SELECT c.id, c.document_id, d.title, c.page_start, c.page_end, c.content, q.query,
                   ts_rank_cd(c.content_tsv, q.query) AS score
            FROM document_chunk c
            JOIN document d ON d.id = c.document_id
            CROSS JOIN websearch_to_tsquery('english', :query) AS q (query)
            WHERE c.content_tsv @@ q.query
              AND %s%s
            ORDER BY score DESC, c.id
            LIMIT :k) r
      ORDER BY r.score DESC, r.id
      """;

  /** Mark every matched word of the chunk, with the markers of {@link Highlights}. */
  private static final String HEADLINE_OPTIONS =
      "HighlightAll=true, StartSel=" + Highlights.START + ", StopSel=" + Highlights.STOP;

  private final JdbcClient jdbc;

  SqlChunkSearch(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  @Transactional(readOnly = true)
  public List<ChunkHit> semantic(CurrentUser user, float[] queryVector, SearchScope scope, int k) {
    return statement(SEMANTIC, user, scope, k)
        .param("embedding", PgVector.text(queryVector))
        .query(SqlChunkSearch::semanticHit)
        .list();
  }

  @Override
  @Transactional(readOnly = true)
  public List<ChunkHit> keyword(CurrentUser user, String query, SearchScope scope, int k) {
    return statement(KEYWORD, user, scope, k)
        .param("query", query)
        .param("headline", HEADLINE_OPTIONS)
        .query(SqlChunkSearch::keywordHit)
        .list();
  }

  /**
   * The statement with the retrievable predicate and the scope filters in its {@code WHERE} clause.
   * Only fixed SQL fragments are put together here; every value is a bound parameter (SEC-7).
   */
  private StatementSpec statement(String template, CurrentUser user, SearchScope scope, int k) {
    StringBuilder filters = new StringBuilder();
    if (!scope.documentIds().isEmpty()) {
      filters.append("\n  AND d.id IN (:documentIds)");
    }
    if (scope.category() != null) {
      filters.append("\n  AND lower(d.category) = :category");
    }
    StatementSpec statement =
        jdbc.sql(template.formatted(RETRIEVABLE, filters))
            .param("uid", user.id())
            .param("role", user.role().name())
            .param("k", k);
    if (!scope.documentIds().isEmpty()) {
      statement = statement.param("documentIds", List.copyOf(scope.documentIds()));
    }
    if (scope.category() != null) {
      statement = statement.param("category", scope.category());
    }
    return statement;
  }

  private static ChunkHit semanticHit(ResultSet row, int rowNum) throws SQLException {
    return new ChunkHit(
        row.getObject("document_id", UUID.class),
        row.getString("title"),
        row.getInt("page_start"),
        row.getInt("page_end"),
        row.getString("content"),
        List.of(),
        row.getDouble("score"));
  }

  /**
   * The headline is the whole chunk with markers around the matched words. If its text ever
   * differed from the stored chunk, the chunk is returned as stored, without highlights, so the
   * text shown is always the document's own.
   */
  private static ChunkHit keywordHit(ResultSet row, int rowNum) throws SQLException {
    String content = row.getString("content");
    Highlights.Marked marked = Highlights.parse(row.getString("marked"));
    boolean same = marked.text().equals(content);
    return new ChunkHit(
        row.getObject("document_id", UUID.class),
        row.getString("title"),
        row.getInt("page_start"),
        row.getInt("page_end"),
        content,
        same ? marked.highlights() : List.of(),
        row.getDouble("score"));
  }
}
