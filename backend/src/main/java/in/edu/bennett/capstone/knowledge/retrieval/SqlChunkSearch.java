package in.edu.bennett.capstone.knowledge.retrieval;

import in.edu.bennett.capstone.knowledge.common.PgVector;
import in.edu.bennett.capstone.knowledge.security.CurrentUser;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
   * The readable predicate of specification 5.3 for the document row with this alias, the one
   * definition for native SQL (ACL-E3): the user can read the document (ACL-1), and admins read
   * every document (D15). The JPA form is {@code RoleAndGrantAccessPolicy}; the access-matrix tests
   * check both.
   */
  private static String readable(String document) {
    return """
        (:role = 'ADMIN'
             OR %1$s.owner_id = :uid
             OR EXISTS (
                  SELECT 1 FROM document_grant g
                  WHERE g.document_id = %1$s.id
                    AND ((g.grantee_type = 'ROLE' AND g.grantee_role = :role)
                      OR (g.grantee_type = 'USER' AND g.grantee_user_id = :uid))))\
        """
        .formatted(document);
  }

  /**
   * The retrievable predicate of specification 5.3, for search and question answering: the document
   * is indexed and not archived (ACL-4), and the user can read it.
   */
  private static final String RETRIEVABLE =
      "d.status = 'INDEXED'\n  AND d.archived = false\n  AND " + readable("d");

  /**
   * What a summary or a comparison may read (ACL-4, ACL-6): an indexed document the user can read.
   * Archived documents are allowed, because the user names the document (DOC-11).
   */
  private static String namedReadable(String document) {
    return document + ".status = 'INDEXED'\n  AND " + readable(document);
  }

  /** SUM-3: every chunk of one document, in the order of the text. */
  private static final String DOCUMENT_CHUNKS =
      """
      SELECT c.document_id, d.title, d.indexed_at, c.chunk_index, c.page_start, c.page_end,
             c.content, c.token_count
      FROM document_chunk c
      JOIN document d ON d.id = c.document_id
      WHERE d.id = :documentId
        AND %s
      ORDER BY c.chunk_index
      """
          .formatted(namedReadable("d"));

  /**
   * CMP-2: the cosine similarity of every chunk of the left document with every chunk of the right
   * one, both readable and indexed. Exact, like search (D11); the size guard keeps both small.
   */
  private static final String CHUNK_SIMILARITIES =
      """
      SELECT l.chunk_index AS left_index, r.chunk_index AS right_index,
             1 - (l.embedding <=> r.embedding) AS similarity
      FROM document_chunk l
      JOIN document dl ON dl.id = l.document_id
      JOIN document_chunk r ON r.document_id = :rightId
      JOIN document dr ON dr.id = r.document_id
      WHERE dl.id = :leftId
        AND %s
        AND %s
      ORDER BY l.chunk_index, r.chunk_index
      """
          .formatted(namedReadable("dl"), namedReadable("dr"));

  /**
   * RET-2, RET-3: exact search, since no vector index exists (D11). The order is the bare distance
   * so that an HNSW index, if one is ever added, could serve it; wrapping the distance in an
   * expression would rule the index out. Similarity is reported as {@code 1 - distance}.
   */
  private static final String SEMANTIC =
      """
      SELECT c.document_id, d.title, d.document_date, c.chunk_index, c.page_start, c.page_end,
             c.content, 1 - (c.embedding <=> CAST(:embedding AS vector)) AS score
      FROM document_chunk c
      JOIN document d ON d.id = c.document_id
      WHERE %1$s%2$s
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
      SELECT r.document_id, r.title, r.document_date, r.chunk_index, r.page_start, r.page_end,
             r.content, r.score, ts_headline('english', r.content, r.query, :headline) AS marked
      FROM (SELECT c.id, c.document_id, d.title, d.document_date, c.chunk_index, c.page_start,
                   c.page_end, c.content, q.query, ts_rank_cd(c.content_tsv, q.query) AS score
            FROM document_chunk c
            JOIN document d ON d.id = c.document_id
            CROSS JOIN websearch_to_tsquery('english', :query) AS q (query)
            WHERE c.content_tsv @@ q.query
              AND %1$s%2$s
            ORDER BY score DESC, c.id
            LIMIT :k) r
      ORDER BY r.score DESC, r.id
      """;

  /**
   * RET-6: Reciprocal Rank Fusion of a semantic and a keyword ranking, in one statement, so both
   * rankings and the result are read in one snapshot. Each ranking has the retrievable predicate in
   * its own {@code WHERE} clause and keeps its best {@code :candidates} chunks; a chunk scores
   * {@code 1 / (:rrf + rank)} for each ranking it is in, and the fused list keeps the best {@code
   * :k}. The outer query joins the fused ids back to their chunks under the same predicate, adds
   * each chunk's cosine similarity for the evidence gate, and marks the matched words.
   *
   * <p>The keyword ranking is keyword mode's own ranking (every word, {@code :query}) followed by
   * the chunks that hold only some of the words ({@code :words}, the query's words joined by {@code
   * or}), so a chunk holding all the words is never outranked by one that repeats a single word
   * (ADR-040). Equal fused scores, a chunk first in one ranking and second in the other against the
   * reverse, keep the keyword order: exact words are the more specific evidence, and the semantic
   * order already decides every chunk keyword search cannot see. Then the order of writing.
   */
  private static final String HYBRID =
      """
      WITH semantic AS (
        SELECT c.id,
               row_number() OVER (ORDER BY c.embedding <=> CAST(:embedding AS vector), c.id) AS rank
        FROM document_chunk c
        JOIN document d ON d.id = c.document_id
        WHERE %1$s%2$s
        ORDER BY c.embedding <=> CAST(:embedding AS vector), c.id
        LIMIT :candidates),
      matched AS (
        SELECT c.id, c.content_tsv @@ q.all_words AS every_word,
               CASE WHEN c.content_tsv @@ q.all_words THEN ts_rank_cd(c.content_tsv, q.all_words)
                    ELSE ts_rank_cd(c.content_tsv, q.any_words) END AS weight
        FROM document_chunk c
        JOIN document d ON d.id = c.document_id
        CROSS JOIN (SELECT websearch_to_tsquery('english', :query) AS all_words,
                           websearch_to_tsquery('english', :words) AS any_words) AS q
        WHERE c.content_tsv @@ q.any_words
          AND %1$s%2$s),
      keyword AS (
        SELECT m.id, row_number() OVER (ORDER BY m.every_word DESC, m.weight DESC, m.id) AS rank
        FROM matched m
        ORDER BY m.every_word DESC, m.weight DESC, m.id
        LIMIT :candidates),
      fused AS (
        SELECT coalesce(s.id, w.id) AS id,
               coalesce(1.0 / (:rrf + s.rank), 0) + coalesce(1.0 / (:rrf + w.rank), 0) AS score,
               s.rank AS semantic_rank, w.rank AS keyword_rank
        FROM semantic s
        FULL OUTER JOIN keyword w ON w.id = s.id),
      best AS (
        SELECT c.id, c.document_id, d.title, d.document_date, c.chunk_index, c.page_start,
               c.page_end, c.content, f.score, f.semantic_rank, f.keyword_rank,
               1 - (c.embedding <=> CAST(:embedding AS vector)) AS similarity
        FROM fused f
        JOIN document_chunk c ON c.id = f.id
        JOIN document d ON d.id = c.document_id
        WHERE %1$s%2$s
        ORDER BY f.score DESC, f.keyword_rank NULLS LAST, f.semantic_rank NULLS LAST, c.id
        LIMIT :k)
      SELECT b.document_id, b.title, b.document_date, b.chunk_index, b.page_start, b.page_end,
             b.content, b.score, b.similarity, b.keyword_rank, b.semantic_rank,
             ts_headline('english', b.content, websearch_to_tsquery('english', :words), :headline)
               AS marked
      FROM best b
      ORDER BY b.score DESC, b.keyword_rank NULLS LAST, b.semantic_rank NULLS LAST, b.id
      """;

  /** Mark every matched word of the chunk, with the markers of {@link Highlights}. */
  private static final String HEADLINE_OPTIONS =
      "HighlightAll=true, StartSel=" + Highlights.START + ", StopSel=" + Highlights.STOP;

  /** The constant of Reciprocal Rank Fusion that RET-6 sets. */
  static final int RRF_CONSTANT = 60;

  private final JdbcClient jdbc;
  private final int candidates;

  SqlChunkSearch(JdbcClient jdbc, RetrievalProperties properties) {
    this.jdbc = jdbc;
    this.candidates = properties.hybridCandidates();
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

  @Override
  @Transactional(readOnly = true)
  public List<ChunkHit> hybrid(
      CurrentUser user,
      float[] queryVector,
      String query,
      String anyWords,
      SearchScope scope,
      int k) {
    return statement(HYBRID, user, scope, k)
        .param("embedding", PgVector.text(queryVector))
        .param("query", query)
        .param("words", anyWords)
        .param("candidates", Math.max(candidates, k))
        .param("rrf", RRF_CONSTANT)
        .param("headline", HEADLINE_OPTIONS)
        .query(SqlChunkSearch::hybridHit)
        .list();
  }

  @Override
  @Transactional(readOnly = true)
  public List<DocumentChunk> documentChunks(CurrentUser user, UUID documentId) {
    return jdbc.sql(DOCUMENT_CHUNKS)
        .param("documentId", documentId)
        .param("uid", user.id())
        .param("role", user.role().name())
        .query(
            (row, rowNum) ->
                new DocumentChunk(
                    row.getObject("document_id", UUID.class),
                    row.getString("title"),
                    row.getObject("indexed_at", OffsetDateTime.class).toInstant(),
                    row.getInt("chunk_index"),
                    row.getInt("page_start"),
                    row.getInt("page_end"),
                    row.getString("content"),
                    row.getInt("token_count")))
        .list();
  }

  @Override
  @Transactional(readOnly = true)
  public List<ChunkSimilarity> similarities(CurrentUser user, UUID leftId, UUID rightId) {
    return jdbc.sql(CHUNK_SIMILARITIES)
        .param("leftId", leftId)
        .param("rightId", rightId)
        .param("uid", user.id())
        .param("role", user.role().name())
        .query(
            (row, rowNum) ->
                new ChunkSimilarity(
                    row.getInt("left_index"),
                    row.getInt("right_index"),
                    row.getDouble("similarity")))
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
      filters.append("\n  AND d.category = :category");
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
      statement = statement.param("category", scope.category().name());
    }
    return statement;
  }

  private static ChunkHit semanticHit(ResultSet row, int rowNum) throws SQLException {
    return new ChunkHit(
        row.getObject("document_id", UUID.class),
        row.getString("title"),
        row.getObject("document_date", LocalDate.class),
        row.getInt("chunk_index"),
        row.getInt("page_start"),
        row.getInt("page_end"),
        row.getString("content"),
        List.of(),
        row.getDouble("score"),
        row.getDouble("score"));
  }

  /** A fused hit, with its cosine similarity and the matched words marked as in keyword mode. */
  private static ChunkHit hybridHit(ResultSet row, int rowNum) throws SQLException {
    ChunkHit marked = keywordHit(row, rowNum);
    return new ChunkHit(
        marked.documentId(),
        marked.title(),
        marked.documentDate(),
        marked.chunkIndex(),
        marked.pageStart(),
        marked.pageEnd(),
        marked.text(),
        marked.highlights(),
        marked.score(),
        row.getDouble("similarity"));
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
        row.getObject("document_date", LocalDate.class),
        row.getInt("chunk_index"),
        row.getInt("page_start"),
        row.getInt("page_end"),
        content,
        same ? marked.highlights() : List.of(),
        row.getDouble("score"),
        null);
  }
}
