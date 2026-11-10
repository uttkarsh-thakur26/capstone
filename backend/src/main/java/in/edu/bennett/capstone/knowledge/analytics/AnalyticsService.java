package in.edu.bennett.capstone.knowledge.analytics;

import in.edu.bennett.capstone.knowledge.ai.ChatGenerator;
import in.edu.bennett.capstone.knowledge.ai.EmbeddingProperties;
import in.edu.bennett.capstone.knowledge.analytics.AnalyticsResponse.CitedDocument;
import in.edu.bennett.capstone.knowledge.analytics.AnalyticsResponse.CostDay;
import in.edu.bennett.capstone.knowledge.analytics.AnalyticsResponse.Count;
import in.edu.bennett.capstone.knowledge.analytics.AnalyticsResponse.CountDay;
import in.edu.bennett.capstone.knowledge.analytics.AnalyticsResponse.Latency;
import in.edu.bennett.capstone.knowledge.analytics.AnalyticsResponse.Period;
import in.edu.bennett.capstone.knowledge.analytics.AnalyticsResponse.QueryDay;
import in.edu.bennett.capstone.knowledge.analytics.AnalyticsResponse.QuestionDay;
import in.edu.bennett.capstone.knowledge.analytics.AnalyticsResponse.SystemInfo;
import in.edu.bennett.capstone.knowledge.analytics.AnalyticsResponse.Totals;
import in.edu.bennett.capstone.knowledge.common.ApiException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The analytics of specification 14.3, for admins only (URL rule and method security, AUTH-8).
 * Every figure is one aggregate statement over a real table: {@code query_log} for questions,
 * searches, tokens, cost and latency, {@code audit_log} for uploads, and the current rows of {@code
 * document}, {@code app_user} and {@code message_citation}. Nothing is estimated or filled in, and
 * a day with nothing is a zero.
 *
 * <p>These statements read the tables of other modules directly, a deliberate exception to 4.3 like
 * the retrieval SQL: each figure is one {@code GROUP BY}, and going through each module's service
 * would add a counting method per figure to five modules (ADR-041). They only read.
 *
 * <p>All statements run in one repeatable-read transaction, so every figure describes the same
 * moment.
 */
@Service
public class AnalyticsService {

  public static final int MAX_DAYS = 366;

  private static final List<String> STATUSES =
      List.of("UPLOADED", "PROCESSING", "INDEXED", "FAILED");
  private static final List<String> ROLES = List.of("ADMIN", "FACULTY", "STUDENT");
  private static final int MOST_CITED = 5;

  private final JdbcClient jdbc;
  private final Clock clock;
  private final ChatGenerator chat;
  private final EmbeddingProperties embedding;

  /** Zone names as the reader sent them, to the database's name for them ("" for none). */
  private final Map<String, String> databaseZones = new ConcurrentHashMap<>();

  AnalyticsService(
      JdbcClient jdbc, Clock clock, ChatGenerator chat, EmbeddingProperties embedding) {
    this.jdbc = jdbc;
    this.clock = clock;
    this.chat = chat;
    this.embedding = embedding;
  }

  /**
   * @param days how many calendar days, today included, from 1 to {@link #MAX_DAYS}
   * @param zone the reader's time zone, an IANA name such as {@code Asia/Kolkata}, so a day is the
   *     reader's day
   */
  @PreAuthorize("hasRole('ADMIN')")
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public AnalyticsResponse analytics(int days, String zone) {
    ZoneId zoneId = validZone(zone);
    LocalDate today = LocalDate.now(clock.withZone(zoneId));
    LocalDate first = today.minusDays(days - 1L);
    Map<String, Object> period = new HashMap<>();
    period.put("from", utc(first.atStartOfDay(zoneId).toInstant()));
    period.put("to", utc(today.plusDays(1).atStartOfDay(zoneId).toInstant()));
    period.put("zone", zoneId.getId());

    List<QuestionDay> questions = questionsPerDay(period, first, today);
    List<QueryDay> queries = queriesPerDay(period, first, today);
    List<CostDay> cost = costPerDay(period, first, today);
    long answered = questions.stream().mapToLong(QuestionDay::answered).sum();
    long noAnswer = questions.stream().mapToLong(QuestionDay::noAnswer).sum();
    long failed = questions.stream().mapToLong(QuestionDay::failed).sum();
    Totals totals =
        new Totals(
            answered + noAnswer + failed,
            answered,
            noAnswer,
            failed,
            answered + noAnswer == 0 ? null : (double) noAnswer / (answered + noAnswer),
            queries.stream()
                .mapToLong(d -> d.keywordSearches() + d.semanticSearches() + d.hybridSearches())
                .sum(),
            queries.stream().mapToLong(QueryDay::summaries).sum(),
            queries.stream().mapToLong(QueryDay::comparisons).sum(),
            cost.stream().mapToLong(CostDay::modelCalls).sum(),
            cost.stream().mapToLong(CostDay::inputTokens).sum(),
            cost.stream().mapToLong(CostDay::cachedInputTokens).sum(),
            cost.stream().mapToLong(CostDay::outputTokens).sum(),
            cost.stream().map(CostDay::costUsd).reduce(BigDecimal.ZERO, BigDecimal::add));

    return new AnalyticsResponse(
        new Period(first, today, zoneId.getId(), firstRecordedAt()),
        totals,
        questions,
        queries,
        cost,
        uploadsPerDay(period, first, today),
        latency(period),
        countsOf("SELECT status AS name, count(*) AS n FROM document GROUP BY status", STATUSES),
        countsOf(
            """
            SELECT failure_code AS name, count(*) AS n FROM document
            WHERE status = 'FAILED' GROUP BY failure_code ORDER BY n DESC, name
            """,
            List.of()),
        countsOf("SELECT role AS name, count(*) AS n FROM app_user GROUP BY role", ROLES),
        mostCited(period),
        new SystemInfo(
            chat.configuredModel(),
            embedding.model(),
            embedding.dimensions(),
            jdbc.sql("SELECT count(*) FROM document").query(Long.class).single(),
            jdbc.sql("SELECT count(*) FROM document_chunk").query(Long.class).single()));
  }

  private List<QuestionDay> questionsPerDay(
      Map<String, Object> period, LocalDate first, LocalDate last) {
    Map<LocalDate, long[]> byDay = new HashMap<>();
    jdbc.sql(
            """
            SELECT (created_at AT TIME ZONE :zone)::date AS day, outcome, count(*) AS n
            FROM query_log
            WHERE type = 'ASK' AND created_at >= :from AND created_at < :to
            GROUP BY 1, 2
            """)
        .params(period)
        .query(
            (row, rowNum) -> {
              long[] counts =
                  byDay.computeIfAbsent(row.getObject("day", LocalDate.class), day -> new long[3]);
              switch (row.getString("outcome")) {
                case "ANSWERED" -> counts[0] += row.getLong("n");
                case "INSUFFICIENT_EVIDENCE" -> counts[1] += row.getLong("n");
                default -> counts[2] += row.getLong("n");
              }
              return null;
            })
        .list();
    return everyDay(
        first,
        last,
        day -> {
          long[] c = byDay.getOrDefault(day, new long[3]);
          return new QuestionDay(day, c[0], c[1], c[2]);
        });
  }

  private List<QueryDay> queriesPerDay(
      Map<String, Object> period, LocalDate first, LocalDate last) {
    Map<LocalDate, Map<String, Long>> byDay = new HashMap<>();
    jdbc.sql(
            """
            SELECT (created_at AT TIME ZONE :zone)::date AS day, type, count(*) AS n
            FROM query_log
            WHERE created_at >= :from AND created_at < :to
            GROUP BY 1, 2
            """)
        .params(period)
        .query(
            (row, rowNum) ->
                byDay
                    .computeIfAbsent(row.getObject("day", LocalDate.class), day -> new HashMap<>())
                    .put(row.getString("type"), row.getLong("n")))
        .list();
    return everyDay(
        first,
        last,
        day -> {
          Map<String, Long> n = byDay.getOrDefault(day, Map.of());
          return new QueryDay(
              day,
              n.getOrDefault("SEARCH_KEYWORD", 0L),
              n.getOrDefault("SEARCH_SEMANTIC", 0L),
              n.getOrDefault("SEARCH_HYBRID", 0L),
              n.getOrDefault("ASK", 0L),
              n.getOrDefault("SUMMARIZE", 0L),
              n.getOrDefault("COMPARE", 0L));
        });
  }

  private List<CostDay> costPerDay(Map<String, Object> period, LocalDate first, LocalDate last) {
    Map<LocalDate, CostDay> byDay = new HashMap<>();
    jdbc.sql(
            """
            SELECT (created_at AT TIME ZONE :zone)::date AS day,
                   sum(model_calls) AS calls,
                   coalesce(sum(input_tokens), 0) AS input,
                   coalesce(sum(cached_input_tokens), 0) AS cached,
                   coalesce(sum(output_tokens), 0) AS output,
                   sum(cost_usd) AS cost
            FROM query_log
            WHERE created_at >= :from AND created_at < :to
            GROUP BY 1
            """)
        .params(period)
        .query(
            (row, rowNum) -> {
              LocalDate day = row.getObject("day", LocalDate.class);
              byDay.put(
                  day,
                  new CostDay(
                      day,
                      row.getLong("calls"),
                      row.getLong("input"),
                      row.getLong("cached"),
                      row.getLong("output"),
                      row.getBigDecimal("cost")));
              return null;
            })
        .list();
    return everyDay(
        first, last, day -> byDay.getOrDefault(day, new CostDay(day, 0, 0, 0, 0, BigDecimal.ZERO)));
  }

  private List<CountDay> uploadsPerDay(
      Map<String, Object> period, LocalDate first, LocalDate last) {
    Map<LocalDate, Long> byDay = new HashMap<>();
    jdbc.sql(
            """
            SELECT (occurred_at AT TIME ZONE :zone)::date AS day, count(*) AS n
            FROM audit_log
            WHERE action = 'DOCUMENT_UPLOADED' AND outcome = 'SUCCESS'
              AND occurred_at >= :from AND occurred_at < :to
            GROUP BY 1
            """)
        .params(period)
        .query((row, rowNum) -> byDay.put(row.getObject("day", LocalDate.class), row.getLong("n")))
        .list();
    return everyDay(first, last, day -> new CountDay(day, byDay.getOrDefault(day, 0L)));
  }

  /**
   * Requests that ran: a summary served from one already made took no model time, and a failed
   * request stopped part way, so either would pull the percentiles of real work down.
   */
  private List<Latency> latency(Map<String, Object> period) {
    return jdbc.sql(
            """
            SELECT type, count(*) AS n,
                   percentile_cont(0.5) WITHIN GROUP (ORDER BY total_ms) AS total_p50,
                   percentile_cont(0.95) WITHIN GROUP (ORDER BY total_ms) AS total_p95,
                   percentile_cont(0.5) WITHIN GROUP (ORDER BY retrieval_ms) AS retrieval_p50,
                   percentile_cont(0.95) WITHIN GROUP (ORDER BY retrieval_ms) AS retrieval_p95,
                   percentile_cont(0.5) WITHIN GROUP (ORDER BY generation_ms) AS generation_p50,
                   percentile_cont(0.95) WITHIN GROUP (ORDER BY generation_ms) AS generation_p95
            FROM query_log
            WHERE created_at >= :from AND created_at < :to
              AND outcome NOT IN ('REUSED', 'ERROR')
            GROUP BY type
            ORDER BY type
            """)
        .params(period)
        .query(
            (row, rowNum) ->
                new Latency(
                    QueryType.valueOf(row.getString("type")),
                    row.getLong("n"),
                    row.getObject("total_p50", Double.class),
                    row.getObject("total_p95", Double.class),
                    row.getObject("retrieval_p50", Double.class),
                    row.getObject("retrieval_p95", Double.class),
                    row.getObject("generation_p50", Double.class),
                    row.getObject("generation_p95", Double.class)))
        .list();
  }

  private List<CitedDocument> mostCited(Map<String, Object> period) {
    Map<String, Object> params = new HashMap<>(period);
    params.put("limit", MOST_CITED);
    return jdbc.sql(
            """
            SELECT c.document_id, d.title, count(*) AS n
            FROM message_citation c
            JOIN message m ON m.id = c.message_id
            JOIN document d ON d.id = c.document_id
            WHERE m.created_at >= :from AND m.created_at < :to
            GROUP BY c.document_id, d.title
            ORDER BY n DESC, d.title, c.document_id
            LIMIT :limit
            """)
        .params(params)
        .query(
            (row, rowNum) ->
                new CitedDocument(
                    row.getObject("document_id", UUID.class),
                    row.getString("title"),
                    row.getLong("n")))
        .list();
  }

  /** Counts by name, every expected name first with zero if absent, then any others found. */
  private List<Count> countsOf(String sql, List<String> expected) {
    Map<String, Long> found = new LinkedHashMap<>();
    expected.forEach(name -> found.put(name, 0L));
    jdbc.sql(sql).query((row, rowNum) -> found.put(row.getString("name"), row.getLong("n"))).list();
    List<Count> counts = new ArrayList<>();
    found.forEach((name, n) -> counts.add(new Count(name, n)));
    return counts;
  }

  private Instant firstRecordedAt() {
    // min() over no rows is one row holding null, which single() would refuse.
    OffsetDateTime first =
        jdbc.sql("SELECT min(created_at) AS first FROM query_log")
            .query((row, rowNum) -> row.getObject("first", OffsetDateTime.class))
            .list()
            .getFirst();
    return first == null ? null : first.toInstant();
  }

  /**
   * A region name the JVM knows, such as {@code Asia/Kolkata}, given back as a name PostgreSQL
   * knows too. Browsers on Windows still report legacy names such as {@code Asia/Calcutta}, which
   * the pinned database image no longer carries (ADR-016); such a name is replaced by the database
   * zone with the same rules, preferring one of the same area. Offsets like {@code +05:30} are
   * refused: PostgreSQL reads POSIX offsets with the opposite sign.
   */
  private ZoneId validZone(String zone) {
    if (zone != null && ZoneId.getAvailableZoneIds().contains(zone)) {
      ZoneId requested = ZoneId.of(zone);
      String known = databaseZones.computeIfAbsent(zone, name -> databaseZoneFor(requested));
      if (!known.isEmpty()) {
        return ZoneId.of(known);
      }
    }
    throw new ApiException(
        HttpStatus.BAD_REQUEST,
        "INVALID_ZONE",
        "zone must be a time zone name such as Asia/Kolkata or UTC.");
  }

  /** The database's name for this zone, or an empty string when it has none with these rules. */
  private String databaseZoneFor(ZoneId zone) {
    List<String> names =
        jdbc.sql("SELECT name FROM pg_timezone_names ORDER BY name").query(String.class).list();
    if (names.contains(zone.getId())) {
      return zone.getId();
    }
    String area = zone.getId().contains("/") ? zone.getId().split("/")[0] + "/" : "";
    String sameRules = "";
    for (String name : names) {
      if (!ZoneId.getAvailableZoneIds().contains(name)
          || !ZoneId.of(name).getRules().equals(zone.getRules())) {
        continue;
      }
      if (name.startsWith(area)) {
        return name;
      }
      if (sameRules.isEmpty()) {
        sameRules = name;
      }
    }
    return sameRules;
  }

  private static <T> List<T> everyDay(LocalDate first, LocalDate last, Function<LocalDate, T> of) {
    List<T> days = new ArrayList<>();
    for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
      days.add(of.apply(day));
    }
    return days;
  }

  private static OffsetDateTime utc(Instant instant) {
    return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
  }
}
