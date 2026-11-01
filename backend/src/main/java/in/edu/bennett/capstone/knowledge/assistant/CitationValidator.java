package in.edu.bennett.capstone.knowledge.assistant;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Citation validation (11.1 step 8). Only numbers of sources that were actually sent survive: a
 * {@code [n]} marker whose number was not sent is removed from the answer text, and so is such a
 * number in the citation list. An answer the model calls unanswerable, or a factual answer with no
 * valid citation left, becomes insufficient evidence.
 */
final class CitationValidator {

  /**
   * A marker of one or more source numbers, {@code [2]} or {@code [1, 3]}, with the spaces in front
   * of it, which go when the marker goes. Numbers have at most three digits, so a year in brackets,
   * such as {@code [2026]}, stays text.
   */
  private static final Pattern MARKER =
      Pattern.compile("([ \\t]*)\\[(\\d{1,3}(?:\\s*,\\s*\\d{1,3})*)]");

  /**
   * A validated answer.
   *
   * @param text the answer with only valid markers
   * @param ordinals the valid source numbers, ascending
   */
  record Validated(String text, List<Integer> ordinals) {}

  private CitationValidator() {}

  /**
   * @return empty when the outcome is insufficient evidence
   */
  static Optional<Validated> validate(ModelAnswer answer, int sourceCount) {
    if (!answer.answerable()) {
      return Optional.empty();
    }
    SortedSet<Integer> valid = new TreeSet<>();
    StringBuilder text = new StringBuilder();
    Matcher marker = MARKER.matcher(answer.answer());
    while (marker.find()) {
      List<Integer> kept = new ArrayList<>();
      for (String number : marker.group(2).split(",")) {
        int n = Integer.parseInt(number.strip());
        if (n >= 1 && n <= sourceCount && !kept.contains(n)) {
          kept.add(n);
        }
      }
      valid.addAll(kept);
      String replacement =
          kept.isEmpty()
              ? ""
              : marker.group(1)
                  + "["
                  + String.join(", ", kept.stream().map(String::valueOf).toList())
                  + "]";
      marker.appendReplacement(text, Matcher.quoteReplacement(replacement));
    }
    marker.appendTail(text);
    for (int n : answer.citations()) {
      if (n >= 1 && n <= sourceCount) {
        valid.add(n);
      }
    }
    String cleaned = text.toString().strip();
    if (valid.isEmpty() || MARKER.matcher(cleaned).replaceAll("").isBlank()) {
      return Optional.empty();
    }
    return Optional.of(new Validated(cleaned, List.copyOf(valid)));
  }
}
