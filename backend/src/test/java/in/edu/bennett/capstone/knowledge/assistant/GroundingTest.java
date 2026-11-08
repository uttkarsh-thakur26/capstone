package in.edu.bennett.capstone.knowledge.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The checks that tie summaries and comparisons to the document's text (ADR-040). */
class GroundingTest {

  @Test
  void markersAreReadInOrderOfFirstAppearance() {
    assertThat(Grounding.markers("A [3] and B [1, 3][2]. A year [2026] is no marker."))
        .containsExactly(3, 1, 2);
  }

  @Test
  void onlyAllowedMarkersAreKept() {
    assertThat(Grounding.keepMarkers("Fees [1][9] and dates [2, 9].", n -> n < 5))
        .isEqualTo("Fees [1] and dates [2].");
    assertThat(Grounding.keepMarkers("Nothing allowed [9].", n -> false))
        .isEqualTo("Nothing allowed.");
  }

  @Test
  void markersAreRenumbered() {
    assertThat(Grounding.renumber("A [14][3] and [3, 7].", Map.of(14, 1, 3, 2)))
        .isEqualTo("A [1][2] and [2].");
  }

  @Test
  void everyNumberMustAppearInTheSources() {
    List<String> sources = List.of("Results on 09 October 2026.", "Fee of 4500 rupees.");
    assertThat(Grounding.numbersAppearIn("Results on 9 October 2026 [1].", sources)).isTrue();
    assertThat(Grounding.numbersAppearIn("A fee of 4500 [2].", sources)).isTrue();
    assertThat(Grounding.numbersAppearIn("A fee of 5000 [2].", sources)).isFalse();
    assertThat(Grounding.numbersAppearIn("Results on 10 October [1].", sources)).isFalse();
    // The marker's own number is not a number of the text.
    assertThat(Grounding.numbersAppearIn("No figures here [7].", sources)).isTrue();
  }

  @Test
  void aQuoteIsFoundIgnoringCaseSpacesAndTypographicMarksAndShownAsStored() {
    String stored = "Results are   published on\n12 October 2026 – on the “portal”.";

    assertThat(Grounding.locate(stored, "PUBLISHED ON 12 october 2026 - on the \"portal\""))
        .hasValue("published on\n12 October 2026 – on the “portal”");
    assertThat(Grounding.locate(stored, "published on 13 October")).isEmpty();
    assertThat(Grounding.locate(stored, "   ")).isEmpty();
  }

  @Test
  void aQuoteIsLookedForInEveryText() {
    List<String> texts = List.of("First passage.", "Phones must be switched off.");

    assertThat(Grounding.inAny("phones MUST be switched off", texts)).isTrue();
    assertThat(Grounding.inAny("phones must be kept in bags", texts)).isFalse();
    assertThat(Grounding.inAny("", texts)).isFalse();
  }
}
