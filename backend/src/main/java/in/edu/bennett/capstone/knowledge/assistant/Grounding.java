package in.edu.bennett.capstone.knowledge.assistant;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The checks that keep a summary's items and a comparison's explanations tied to the document's own
 * text (Section 13, ADR-040). Each is plain code, so a model cannot talk its way past it.
 */
final class Grounding {

  /** A run of digits, the unit the number check compares. */
  private static final Pattern NUMBER = Pattern.compile("\\p{Nd}+");

  private Grounding() {}

  /** Every source number in the text's {@code [n]} markers, in order of first appearance. */
  static List<Integer> markers(String text) {
    Set<Integer> numbers = new LinkedHashSet<>();
    Matcher marker = CitationValidator.MARKER.matcher(text);
    while (marker.find()) {
      for (String number : marker.group(2).split(",")) {
        numbers.add(Integer.parseInt(number.strip()));
      }
    }
    return List.copyOf(numbers);
  }

  /**
   * The text with only the markers of allowed numbers; a marker left with none goes, with the
   * spaces in front of it.
   */
  static String keepMarkers(String text, IntPredicate allowed) {
    StringBuilder kept = new StringBuilder();
    Matcher marker = CitationValidator.MARKER.matcher(text);
    while (marker.find()) {
      List<String> numbers = new ArrayList<>();
      for (String number : marker.group(2).split(",")) {
        int n = Integer.parseInt(number.strip());
        if (allowed.test(n) && !numbers.contains(String.valueOf(n))) {
          numbers.add(String.valueOf(n));
        }
      }
      String replacement =
          numbers.isEmpty() ? "" : marker.group(1) + "[" + String.join(", ", numbers) + "]";
      marker.appendReplacement(kept, Matcher.quoteReplacement(replacement));
    }
    marker.appendTail(kept);
    return kept.toString().strip();
  }

  /** The text with each marker's numbers replaced through the map; unknown numbers are dropped. */
  static String renumber(String text, Map<Integer, Integer> numbers) {
    StringBuilder renumbered = new StringBuilder();
    Matcher marker = CitationValidator.MARKER.matcher(text);
    while (marker.find()) {
      List<String> mapped = new ArrayList<>();
      for (String number : marker.group(2).split(",")) {
        Integer n = numbers.get(Integer.parseInt(number.strip()));
        if (n != null && !mapped.contains(String.valueOf(n))) {
          mapped.add(String.valueOf(n));
        }
      }
      String replacement =
          mapped.isEmpty() ? "" : marker.group(1) + "[" + String.join(", ", mapped) + "]";
      marker.appendReplacement(renumbered, Matcher.quoteReplacement(replacement));
    }
    marker.appendTail(renumbered);
    return renumbered.toString().strip();
  }

  /** Whether anything but markers and spaces is left. */
  static boolean hasText(String text) {
    return !CitationValidator.withoutMarkers(text).isBlank();
  }

  /**
   * The number check: every number in the text, markers aside, appears in at least one of the
   * sources, so a figure, date or threshold the sources do not hold is caught. Leading zeros are
   * ignored, so "9" matches "09". Numbers spelled out in words are not checked.
   */
  static boolean numbersAppearIn(String text, List<String> sources) {
    Set<String> known = new HashSet<>();
    for (String source : sources) {
      known.addAll(numbersOf(source));
    }
    return known.containsAll(numbersOf(CitationValidator.withoutMarkers(text)));
  }

  private static Set<String> numbersOf(String text) {
    Set<String> numbers = new HashSet<>();
    Matcher number = NUMBER.matcher(text);
    while (number.find()) {
      String digits = number.group().replaceFirst("^0+(?=\\d)", "");
      numbers.add(digits);
    }
    return numbers;
  }

  /**
   * Text as a quote is compared: white space runs made one space, typographic quotes and dashes
   * made plain, lower case. A quote must still be the document's own words in their order.
   */
  static String comparable(String text) {
    return normalize(text).text().strip();
  }

  /**
   * Finds a quote in a document's text, compared as {@link #comparable} does, and returns the
   * document's own characters for it, so what is shown is the stored text, never the model's copy
   * of it (RAG-3).
   */
  static Optional<String> locate(String original, String quote) {
    String wanted = comparable(quote);
    if (wanted.isEmpty()) {
      return Optional.empty();
    }
    Normalized text = normalize(original);
    int at = text.text().indexOf(wanted);
    if (at < 0) {
      return Optional.empty();
    }
    int start = text.origin()[at];
    int end = text.origin()[at + wanted.length() - 1] + 1;
    return Optional.of(original.substring(start, end));
  }

  /** Whether the quote, compared as {@link #comparable} does, is in any of the texts. */
  static boolean inAny(String quote, List<String> texts) {
    String wanted = comparable(quote);
    return !wanted.isEmpty() && texts.stream().anyMatch(t -> normalize(t).text().contains(wanted));
  }

  /**
   * The comparable form and, for each of its characters, where it came from in the original. Every
   * character maps to one, so positions carry back exactly.
   */
  private record Normalized(String text, int[] origin) {}

  private static Normalized normalize(String text) {
    StringBuilder out = new StringBuilder(text.length());
    int[] origin = new int[text.length()];
    boolean space = false;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (Character.isWhitespace(c) || Character.isSpaceChar(c)) {
        if (!out.isEmpty() && !space) {
          origin[out.length()] = i;
          out.append(' ');
        }
        space = true;
        continue;
      }
      space = false;
      origin[out.length()] = i;
      out.append(plain(c));
    }
    return new Normalized(out.toString(), origin);
  }

  private static char plain(char c) {
    return switch (c) {
      case '‘', '’', '‛', '′' -> '\'';
      case '“', '”', '‟', '″' -> '"';
      case '‐', '‑', '‒', '–', '—', '―', '−' -> '-';
      default -> Character.toLowerCase(c);
    };
  }
}
