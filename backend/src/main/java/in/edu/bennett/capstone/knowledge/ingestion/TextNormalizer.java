package in.edu.bennett.capstone.knowledge.ingestion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * ING-5: cleans extracted text without changing what it says.
 *
 * <ul>
 *   <li>Line breaks become {@code \n} whatever the platform, and control characters other than tab
 *       and line break are removed: PostgreSQL text cannot hold NUL.
 *   <li>Lines that repeat as a header or footer on most pages are dropped. A line counts only among
 *       the first or last two lines of a page, and must be identical on those pages; only a line
 *       that is a page number ("7", "Page 7 of 12", "- 7 -") may differ in its digits.
 *   <li>A word hyphenated across a line break is joined, but only a letter, hyphen, line break and
 *       lower-case letter: "2025-\n26" and "COVID-\n19" stay as they are.
 *   <li>The lines of a paragraph are joined with a space, paragraphs are separated by one blank
 *       line, and runs of spaces become one space.
 * </ul>
 *
 * Numbers, dates, clause numbers and casing are never changed. Table layout is lost in text
 * extraction; that is a known limitation.
 */
@Component
class TextNormalizer {

  /** Header and footer detection needs this many pages; with fewer, nothing repeats "on most". */
  private static final int MIN_PAGES_FOR_REPEATS = 3;

  /** How many lines at the top and the bottom of a page may be a header or footer. */
  private static final int EDGE_LINES = 2;

  private static final Pattern CONTROL = Pattern.compile("[\\p{Cc}&&[^\\n\\t]]|\\u00AD");
  private static final Pattern SPACES = Pattern.compile("[ \\t\\u00A0\\u2000-\\u200B\\u3000]+");
  private static final Pattern PAGE_NUMBER =
      Pattern.compile("(?i)^[-–—\\s]*(page\\s*)?\\d+(\\s*(of|/)\\s*\\d+)?[-–—\\s]*$");
  private static final Pattern HYPHENATED = Pattern.compile("(\\p{L})-\\n(\\p{Ll})");
  private static final Pattern DIGITS = Pattern.compile("\\d+");

  List<PageText> normalize(List<PageText> pages) {
    List<List<String>> lines = new ArrayList<>();
    for (PageText page : pages) {
      lines.add(lines(page.text()));
    }
    Set<String> repeated = repeatedEdgeLines(lines);
    List<PageText> normalized = new ArrayList<>();
    for (int i = 0; i < pages.size(); i++) {
      List<String> kept = dropEdges(lines.get(i), repeated);
      String text = paragraphs(String.join("\n", kept));
      if (!text.isBlank()) {
        normalized.add(new PageText(pages.get(i).page(), text));
      }
    }
    return normalized;
  }

  private static List<String> lines(String text) {
    String clean = CONTROL.matcher(text.replace("\r\n", "\n").replace('\r', '\n')).replaceAll("");
    List<String> lines = new ArrayList<>();
    for (String line : clean.split("\n", -1)) {
      lines.add(SPACES.matcher(line).replaceAll(" ").strip());
    }
    return lines;
  }

  /** The keys of edge lines found on more than half of the pages. */
  private static Set<String> repeatedEdgeLines(List<List<String>> pages) {
    if (pages.size() < MIN_PAGES_FOR_REPEATS) {
      return Set.of();
    }
    Map<String, Integer> pagesWithLine = new HashMap<>();
    for (List<String> page : pages) {
      Set<String> keys = new HashSet<>();
      for (int index : edgeIndexes(page)) {
        keys.add(key(page.get(index)));
      }
      keys.forEach(key -> pagesWithLine.merge(key, 1, Integer::sum));
    }
    Set<String> repeated = new HashSet<>();
    pagesWithLine.forEach(
        (key, count) -> {
          if (count * 2 > pages.size()) {
            repeated.add(key);
          }
        });
    return repeated;
  }

  private static List<String> dropEdges(List<String> page, Set<String> repeated) {
    if (repeated.isEmpty()) {
      return page;
    }
    Set<Integer> drop = new HashSet<>();
    for (int index : edgeIndexes(page)) {
      if (repeated.contains(key(page.get(index)))) {
        drop.add(index);
      }
    }
    List<String> kept = new ArrayList<>();
    for (int i = 0; i < page.size(); i++) {
      if (!drop.contains(i)) {
        kept.add(page.get(i));
      }
    }
    return kept;
  }

  /** The positions of the first and last non-blank lines of a page. */
  private static Set<Integer> edgeIndexes(List<String> page) {
    List<Integer> nonBlank = new ArrayList<>();
    for (int i = 0; i < page.size(); i++) {
      if (!page.get(i).isEmpty()) {
        nonBlank.add(i);
      }
    }
    Set<Integer> edges = new HashSet<>();
    for (int i = 0; i < Math.min(EDGE_LINES, nonBlank.size()); i++) {
      edges.add(nonBlank.get(i));
      edges.add(nonBlank.get(nonBlank.size() - 1 - i));
    }
    return edges;
  }

  /** Lines compare as written; only a page number compares with its digits masked. */
  private static String key(String line) {
    return PAGE_NUMBER.matcher(line).matches()
        ? "#page " + DIGITS.matcher(line.toLowerCase(Locale.ROOT)).replaceAll("#")
        : line;
  }

  private static String paragraphs(String text) {
    String joined = HYPHENATED.matcher(text).replaceAll("$1$2");
    List<String> paragraphs = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    for (String line : joined.split("\n", -1)) {
      if (line.isEmpty()) {
        flush(current, paragraphs);
      } else {
        if (!current.isEmpty()) {
          current.append(' ');
        }
        current.append(line);
      }
    }
    flush(current, paragraphs);
    return String.join("\n\n", paragraphs);
  }

  private static void flush(StringBuilder current, List<String> paragraphs) {
    if (!current.isEmpty()) {
      paragraphs.add(current.toString());
      current.setLength(0);
    }
  }
}
