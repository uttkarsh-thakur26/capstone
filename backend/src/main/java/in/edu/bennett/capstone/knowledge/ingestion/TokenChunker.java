package in.edu.bennett.capstone.knowledge.ingestion;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.ai.tokenizer.TokenCountEstimator;

/**
 * D10: chunks of at most {@code size} tokens, each starting with the last {@code overlap} tokens of
 * the one before, cut at sentence boundaries. Spring AI's {@code TokenTextSplitter} has no overlap
 * (3.3), hence this class.
 *
 * <p>The text is cut into sentences, each remembering its page and whether it starts a paragraph,
 * and sentences are packed into chunks while the chunk, counted exactly, stays within {@code size}.
 * The next chunk starts with the last whole sentences that fit in {@code overlap}, or the last
 * words of the final sentence when even that one is longer. A sentence longer than a whole chunk is
 * cut at word boundaries. A chunk's page range is that of its sentences, so a chunk spans pages
 * only when its text does.
 */
class TokenChunker implements Chunker {

  private final TokenCountEstimator tokens;
  private final int size;
  private final int overlap;

  TokenChunker(TokenCountEstimator tokens, int size, int overlap) {
    if (overlap >= size) {
      throw new IllegalArgumentException("The overlap must be smaller than the chunk size");
    }
    this.tokens = tokens;
    this.size = size;
    this.overlap = overlap;
  }

  /** A sentence, or part of one. */
  private record Unit(String text, int page, boolean startsParagraph) {}

  @Override
  public List<Chunk> split(List<PageText> pages) {
    List<Chunk> chunks = new ArrayList<>();
    List<Unit> current = new ArrayList<>();
    boolean hasNew = false;
    for (Unit unit : units(pages)) {
      List<Unit> candidate = new ArrayList<>(current);
      candidate.add(unit);
      if (!current.isEmpty() && count(candidate) > size) {
        if (hasNew) {
          chunks.add(chunk(chunks.size(), current));
        }
        current = overlapOf(current);
        candidate = new ArrayList<>(current);
        candidate.add(unit);
        if (count(candidate) > size) {
          current = new ArrayList<>();
        }
      }
      current.add(unit);
      hasNew = true;
    }
    if (hasNew && !current.isEmpty()) {
      chunks.add(chunk(chunks.size(), current));
    }
    return chunks;
  }

  /** Sentences in order, none longer than a chunk. */
  private List<Unit> units(List<PageText> pages) {
    List<Unit> units = new ArrayList<>();
    for (PageText page : pages) {
      for (String paragraph : page.text().split("\n\n")) {
        boolean first = true;
        for (String sentence : sentences(paragraph)) {
          for (String piece : fitting(sentence, size)) {
            units.add(new Unit(piece, page.page(), first));
            first = false;
          }
        }
      }
    }
    return units;
  }

  private static List<String> sentences(String paragraph) {
    BreakIterator boundaries = BreakIterator.getSentenceInstance(Locale.ENGLISH);
    boundaries.setText(paragraph);
    List<String> sentences = new ArrayList<>();
    int start = boundaries.first();
    for (int end = boundaries.next();
        end != BreakIterator.DONE;
        start = end, end = boundaries.next()) {
      String sentence = paragraph.substring(start, end).strip();
      if (!sentence.isEmpty()) {
        sentences.add(sentence);
      }
    }
    return sentences;
  }

  /** The text cut at word boundaries into pieces of at most {@code limit} tokens. */
  private List<String> fitting(String text, int limit) {
    if (tokens.estimate(text) <= limit) {
      return List.of(text);
    }
    List<String> pieces = new ArrayList<>();
    StringBuilder piece = new StringBuilder();
    for (String word : text.split(" ")) {
      String candidate = piece.isEmpty() ? word : piece + " " + word;
      if (tokens.estimate(candidate) <= limit) {
        piece.setLength(0);
        piece.append(candidate);
        continue;
      }
      if (!piece.isEmpty()) {
        pieces.add(piece.toString());
        piece.setLength(0);
      }
      if (tokens.estimate(word) <= limit) {
        piece.append(word);
      } else {
        pieces.addAll(characters(word, limit));
      }
    }
    if (!piece.isEmpty()) {
      pieces.add(piece.toString());
    }
    return pieces;
  }

  /** A "word" longer than a chunk, such as a long run of symbols, cut by characters. */
  private List<String> characters(String word, int limit) {
    List<String> pieces = new ArrayList<>();
    int start = 0;
    while (start < word.length()) {
      int end = word.length();
      while (tokens.estimate(word.substring(start, end)) > limit) {
        end = start + Math.max(1, (end - start) / 2);
      }
      pieces.add(word.substring(start, end));
      start = end;
    }
    return pieces;
  }

  /** The last units of a chunk that fit in the overlap. */
  private List<Unit> overlapOf(List<Unit> chunk) {
    List<Unit> tail = new ArrayList<>();
    for (int i = chunk.size() - 1; i >= 0; i--) {
      List<Unit> candidate = new ArrayList<>();
      candidate.add(chunk.get(i));
      candidate.addAll(tail);
      if (count(candidate) > overlap) {
        break;
      }
      tail = candidate;
    }
    if (tail.isEmpty() && overlap > 0) {
      Unit last = chunk.getLast();
      String[] words = last.text().split(" ");
      String end = "";
      for (int i = words.length - 1; i > 0; i--) {
        String candidate = end.isEmpty() ? words[i] : words[i] + " " + end;
        if (tokens.estimate(candidate) > overlap) {
          break;
        }
        end = candidate;
      }
      if (!end.isEmpty()) {
        tail.add(new Unit(end, last.page(), false));
      }
    }
    return tail;
  }

  private int count(List<Unit> units) {
    return tokens.estimate(text(units));
  }

  private static String text(List<Unit> units) {
    StringBuilder text = new StringBuilder();
    for (Unit unit : units) {
      if (!text.isEmpty()) {
        text.append(unit.startsParagraph() ? "\n\n" : " ");
      }
      text.append(unit.text());
    }
    return text.toString();
  }

  private Chunk chunk(int index, List<Unit> units) {
    String content = text(units);
    int pageStart = units.stream().mapToInt(Unit::page).min().orElseThrow();
    int pageEnd = units.stream().mapToInt(Unit::page).max().orElseThrow();
    return new Chunk(index, pageStart, pageEnd, content, tokens.estimate(content));
  }
}
