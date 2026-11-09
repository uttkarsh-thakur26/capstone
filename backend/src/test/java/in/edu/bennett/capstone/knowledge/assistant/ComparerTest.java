package in.edu.bennett.capstone.knowledge.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import in.edu.bennett.capstone.knowledge.assistant.Comparer.Alignment;
import in.edu.bennett.capstone.knowledge.assistant.Comparer.Candidate;
import in.edu.bennett.capstone.knowledge.retrieval.ChunkSimilarity;
import in.edu.bennett.capstone.knowledge.retrieval.DocumentChunk;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** The alignment step of a comparison (CMP-2, ADR-040): mutual closeness, above a low floor. */
class ComparerTest {

  private final Comparer comparer =
      new Comparer(
          null, JsonMapper.builder().build(), new ComparisonProperties(30, 16000, 0.2, 15, 240));

  /**
   * The similarities measured on 2026-11-08 between the CSET109 course plan (4 chunks) and the
   * CSET305 syllabus (3 chunks) with qwen3-embedding:0.6b: none above 0.31. Left 3 is closest to
   * right 0, but right 0 is closer to left 0, so left 3 stays on its own; the other three pair up.
   */
  @Test
  void twoDocumentsOfOneTemplatePairTheirMatchingSections() {
    List<DocumentChunk> left = chunks("L", 4);
    List<DocumentChunk> right = chunks("R", 3);
    List<ChunkSimilarity> measured =
        List.of(
            new ChunkSimilarity(0, 0, 0.302),
            new ChunkSimilarity(3, 0, 0.285),
            new ChunkSimilarity(2, 2, 0.252),
            new ChunkSimilarity(1, 1, 0.247),
            new ChunkSimilarity(3, 1, 0.229),
            new ChunkSimilarity(1, 0, 0.223),
            new ChunkSimilarity(2, 0, 0.217),
            new ChunkSimilarity(2, 1, 0.217),
            new ChunkSimilarity(0, 2, 0.214),
            new ChunkSimilarity(3, 2, 0.212),
            new ChunkSimilarity(0, 1, 0.208),
            new ChunkSimilarity(1, 2, 0.196));

    Alignment alignment = comparer.align(left, right, measured);

    assertThat(alignment.candidates())
        .extracting(Candidate::n, c -> text(c.left()), c -> text(c.right()))
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(1, "L0", "R0"),
            org.assertj.core.groups.Tuple.tuple(2, "L1", "R1"),
            org.assertj.core.groups.Tuple.tuple(3, "L2", "R2"),
            org.assertj.core.groups.Tuple.tuple(4, "L3", null));
    assertThat(alignment.identical()).isZero();
  }

  /**
   * Two versions: the same passage reads the same and is dropped; a changed passage pairs with its
   * new version; a new passage closest to an old one that already has its own partner is on the
   * right only; nothing below the floor pairs.
   */
  @Test
  void twoVersionsPairTheSamePassagesAndDropWhatReadsTheSame() {
    List<DocumentChunk> left =
        List.of(chunk(0, "The exam lasts 55 minutes."), chunk(1, "Results on 9 October."));
    List<DocumentChunk> right =
        List.of(
            chunk(0, "  The exam lasts 55   minutes. "),
            chunk(1, "Results on 12 October."),
            chunk(2, "Calculators are allowed."));
    List<ChunkSimilarity> similarities =
        List.of(
            new ChunkSimilarity(0, 0, 1.0),
            new ChunkSimilarity(1, 1, 0.93),
            new ChunkSimilarity(1, 2, 0.41),
            new ChunkSimilarity(0, 2, 0.15),
            new ChunkSimilarity(0, 1, 0.3),
            new ChunkSimilarity(1, 0, 0.3));

    Alignment alignment = comparer.align(left, right, similarities);

    assertThat(alignment.identical()).isOne();
    assertThat(alignment.candidates())
        .extracting(c -> text(c.left()), c -> text(c.right()))
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("Results on 9 October.", "Results on 12 October."),
            org.assertj.core.groups.Tuple.tuple(null, "Calculators are allowed."));
  }

  @Test
  void passagesBelowTheFloorStayOnTheirOwnSide() {
    Alignment alignment =
        comparer.align(
            List.of(chunk(0, "Left only.")),
            List.of(chunk(0, "Right only.")),
            List.of(new ChunkSimilarity(0, 0, 0.19)));

    assertThat(alignment.candidates())
        .extracting(c -> text(c.left()), c -> text(c.right()))
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("Left only.", null),
            org.assertj.core.groups.Tuple.tuple(null, "Right only."));
  }

  private static List<DocumentChunk> chunks(String prefix, int count) {
    return java.util.stream.IntStream.range(0, count).mapToObj(i -> chunk(i, prefix + i)).toList();
  }

  private static DocumentChunk chunk(int index, String text) {
    return new DocumentChunk(
        UUID.randomUUID(), "Doc", Instant.EPOCH, index, index + 1, index + 1, text, 10);
  }

  private static String text(DocumentChunk chunk) {
    return chunk == null ? null : chunk.text();
  }
}
