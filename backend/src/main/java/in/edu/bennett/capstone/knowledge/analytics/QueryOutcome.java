package in.edu.bennett.capstone.knowledge.analytics;

/**
 * How a logged request ended. Questions end {@code ANSWERED}, {@code INSUFFICIENT_EVIDENCE} ("No
 * answer found") or {@code ERROR}; searches, summaries and comparisons {@code OK} or {@code ERROR};
 * a summary request served by a summary that exists or is being made is {@code REUSED}.
 */
public enum QueryOutcome {
  OK,
  ANSWERED,
  INSUFFICIENT_EVIDENCE,
  REUSED,
  ERROR
}
