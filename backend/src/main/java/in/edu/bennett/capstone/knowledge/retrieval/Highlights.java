package in.edu.bennett.capstone.knowledge.retrieval;

import in.edu.bennett.capstone.knowledge.retrieval.SearchResponse.Highlight;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns the output of PostgreSQL's {@code ts_headline} into plain text and the positions of the
 * matched words (ADR-035). The two markers are control characters, which the text normalizer
 * removes from every chunk (ING-5), so they cannot clash with document text, and no markup ever
 * reaches the client (UI-6).
 */
final class Highlights {

  /** Put before each matched word, as {@code ts_headline}'s {@code StartSel}. */
  static final char START = '\u0002';

  /** Put after each matched word, as {@code ts_headline}'s {@code StopSel}. */
  static final char STOP = '\u0003';

  /** The text without markers, and where the marked words are in it. */
  record Marked(String text, List<Highlight> highlights) {}

  private Highlights() {}

  static Marked parse(String marked) {
    StringBuilder text = new StringBuilder(marked.length());
    List<Highlight> highlights = new ArrayList<>();
    int start = -1;
    for (int i = 0; i < marked.length(); i++) {
      char c = marked.charAt(i);
      if (c == START) {
        start = text.length();
      } else if (c == STOP) {
        if (start >= 0 && text.length() > start) {
          highlights.add(new Highlight(start, text.length()));
        }
        start = -1;
      } else {
        text.append(c);
      }
    }
    return new Marked(text.toString(), List.copyOf(highlights));
  }
}
