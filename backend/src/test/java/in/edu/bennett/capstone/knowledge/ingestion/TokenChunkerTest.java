package in.edu.bennett.capstone.knowledge.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import in.edu.bennett.capstone.knowledge.ingestion.Chunker.Chunk;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;

/** D10, ING-6: size, overlap, sentence boundaries and page ranges. */
class TokenChunkerTest {

  private final TokenCountEstimator tokens = new JTokkitTokenCountEstimator();
  private final TokenChunker chunker = new TokenChunker(tokens, 400, 60);

  @Test
  void everyChunkFitsAndCountsItsTokensExactly() {
    List<Chunk> chunks = chunker.split(List.of(new PageText(1, sentences("Alpha", 120))));

    assertThat(chunks).hasSizeGreaterThan(3);
    for (Chunk chunk : chunks) {
      assertThat(chunk.tokenCount())
          .isLessThanOrEqualTo(400)
          .isEqualTo(tokens.estimate(chunk.content()));
      assertThat(chunk.content()).isNotBlank();
    }
    for (int i = 0; i < chunks.size(); i++) {
      assertThat(chunks.get(i).index()).isEqualTo(i);
    }
  }

  @Test
  void eachChunkStartsWithTheEndOfThePreviousOneAndCutsAtSentences() {
    List<Chunk> chunks = chunker.split(List.of(new PageText(1, sentences("Alpha", 80))));

    for (int i = 1; i < chunks.size(); i++) {
      String previous = chunks.get(i - 1).content();
      String current = chunks.get(i).content();
      String firstSentence = current.substring(0, current.indexOf('.') + 1);
      assertThat(previous).endsWith(overlapEnd(previous, current));
      assertThat(previous).contains(firstSentence);
      assertThat(tokens.estimate(overlapEnd(previous, current))).isBetween(1, 60);
      assertThat(current).endsWith(".");
    }
  }

  @Test
  void aChunkSpansPagesOnlyWhenItsTextDoes() {
    List<Chunk> chunks =
        chunker.split(
            List.of(
                new PageText(1, sentences("One", 5)),
                new PageText(2, sentences("Two", 5)),
                new PageText(4, sentences("Four", 40))));

    Chunk first = chunks.getFirst();
    assertThat(first.pageStart()).isEqualTo(1);
    assertThat(first.pageEnd()).isEqualTo(4);
    for (Chunk chunk : chunks) {
      assertThat(chunk.content().contains("One sentence")).isEqualTo(chunk.pageStart() == 1);
      assertThat(chunk.content().contains("Four sentence")).isEqualTo(chunk.pageEnd() == 4);
      assertThat(chunk.pageStart()).isNotEqualTo(3);
    }
  }

  @Test
  void aSentenceLongerThanAChunkIsCutAtWords() {
    String endless = "word ".repeat(1500).strip() + ".";

    List<Chunk> chunks = chunker.split(List.of(new PageText(1, endless)));

    assertThat(chunks).hasSizeGreaterThan(3);
    assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.tokenCount()).isLessThanOrEqualTo(400));
    assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.content()).doesNotContain("wor d"));
  }

  @Test
  void aWordLongerThanAChunkIsCutByCharacters() {
    List<Chunk> chunks = chunker.split(List.of(new PageText(1, "x9".repeat(3000))));

    assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.tokenCount()).isLessThanOrEqualTo(400));
    assertThat(String.join("", chunks.stream().map(Chunk::content).toList())).contains("x9x9");
  }

  @Test
  void paragraphsStaySeparatedInsideAChunk() {
    List<Chunk> chunks =
        chunker.split(List.of(new PageText(1, "First paragraph.\n\nSecond paragraph.")));

    assertThat(chunks)
        .singleElement()
        .extracting(Chunk::content)
        .isEqualTo("First paragraph.\n\nSecond paragraph.");
  }

  @Test
  void noTextGivesNoChunks() {
    assertThat(chunker.split(List.of())).isEmpty();
  }

  @Test
  void theOverlapMustBeSmallerThanTheChunk() {
    assertThatIllegalArgumentException().isThrownBy(() -> new TokenChunker(tokens, 60, 60));
  }

  /** The text the current chunk repeats from the end of the previous one. */
  private static String overlapEnd(String previous, String current) {
    for (int length = Math.min(previous.length(), current.length()); length > 0; length--) {
      if (previous.endsWith(current.substring(0, length))) {
        return current.substring(0, length);
      }
    }
    return "";
  }

  private static String sentences(String marker, int count) {
    List<String> sentences = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      sentences.add(marker + " sentence " + i + " says the fee is 500 rupees under clause 7.3(b).");
    }
    return String.join(" ", sentences);
  }
}
