package in.edu.bennett.capstone.knowledge.analytics;

/** What a {@code query_log} row records (specification 7.2). The table's check lists the same. */
public enum QueryType {
  SEARCH_KEYWORD,
  SEARCH_SEMANTIC,
  SEARCH_HYBRID,
  ASK,
  SUMMARIZE,
  COMPARE
}
