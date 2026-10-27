package in.edu.bennett.capstone.knowledge.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** ING-5: what normalization changes, and what it must leave alone. */
class TextNormalizerTest {

  private final TextNormalizer normalizer = new TextNormalizer();

  @Test
  void repeatedHeadersAndPageNumbersAreDropped() {
    List<PageText> pages = new ArrayList<>();
    for (int p = 1; p <= 4; p++) {
      pages.add(
          new PageText(
              p,
              "Bennett University Examination Rules\r\n\r\nBody of page "
                  + p
                  + " with its own text.\r\n\r\nPage "
                  + p
                  + " of 4"));
    }

    List<PageText> normalized = normalizer.normalize(pages);

    assertThat(normalized)
        .extracting(PageText::text)
        .containsExactly(
            "Body of page 1 with its own text.",
            "Body of page 2 with its own text.",
            "Body of page 3 with its own text.",
            "Body of page 4 with its own text.");
    assertThat(normalized).extracting(PageText::page).containsExactly(1, 2, 3, 4);
  }

  @Test
  void aRepeatedLineWhoseNumbersChangeIsKept() {
    List<PageText> pages = new ArrayList<>();
    for (int p = 1; p <= 4; p++) {
      pages.add(new PageText(p, "Total fee " + (p * 1200) + " rupees\nOther text " + p));
    }

    List<PageText> normalized = normalizer.normalize(pages);

    assertThat(normalized.get(2).text()).isEqualTo("Total fee 3600 rupees Other text 3");
  }

  @Test
  void withFewPagesNothingCountsAsRepeated() {
    List<PageText> normalized =
        normalizer.normalize(
            List.of(new PageText(1, "Notice\nFirst"), new PageText(2, "Notice\nSecond")));

    assertThat(normalized)
        .extracting(PageText::text)
        .containsExactly("Notice First", "Notice Second");
  }

  @Test
  void wordsHyphenatedAcrossLinesAreJoinedButNumbersAndCapitalsAreNot() {
    List<PageText> normalized =
        normalizer.normalize(
            List.of(
                new PageText(
                    1,
                    "The gover-\nnance board met in 2025-\n26 about COVID-\n19 and Clause 4.2(a).")));

    assertThat(normalized.getFirst().text())
        .isEqualTo("The governance board met in 2025- 26 about COVID- 19 and Clause 4.2(a).");
  }

  @Test
  void whitespaceIsCollapsedAndControlCharactersRemoved() {
    List<PageText> normalized =
        normalizer.normalize(
            List.of(new PageText(1, "  Deadline:\u0000   12 March   2026 \t\n\n\n\nRoom B-204  ")));

    assertThat(normalized.getFirst().text()).isEqualTo("Deadline: 12 March 2026\n\nRoom B-204");
  }

  @Test
  void aPageLeftWithoutTextIsDropped() {
    assertThat(normalizer.normalize(List.of(new PageText(1, " \n\u0000 \n")))).isEmpty();
  }
}
