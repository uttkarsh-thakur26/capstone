package in.edu.bennett.capstone.knowledge.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import in.edu.bennett.capstone.knowledge.assistant.CitationValidator.Validated;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Citation validation, 11.1 step 8. */
class CitationValidatorTest {

  @Test
  void validCitationsAreKeptInAscendingOrder() {
    assertThat(
            validate(true, "Seventy-five percent [2]. Medical leave counts [1].", List.of(2, 1), 3))
        .contains(
            new Validated("Seventy-five percent [2]. Medical leave counts [1].", List.of(1, 2)));
  }

  /** 19.2: invalid citation numbers are dropped, from the list and from the text. */
  @Test
  void numbersOfSourcesThatWereNotSentAreDropped() {
    assertThat(
            validate(
                true,
                "Fees are due in July [1][7]. Late fees apply [9].",
                List.of(1, 7, 9, 0, -2),
                2))
        .contains(new Validated("Fees are due in July [1]. Late fees apply.", List.of(1)));
    assertThat(validate(true, "Both say so [1, 4, 2, 1].", List.of(), 2))
        .contains(new Validated("Both say so [1, 2].", List.of(1, 2)));
  }

  @Test
  void aCitationOnlyInTheListCounts() {
    assertThat(validate(true, "The deadline is 9 October 2026.", List.of(3), 3))
        .contains(new Validated("The deadline is 9 October 2026.", List.of(3)));
  }

  @Test
  void aYearInBracketsIsNotAMarker() {
    assertThat(validate(true, "Regulations [2026] apply [1].", List.of(1), 1))
        .contains(new Validated("Regulations [2026] apply [1].", List.of(1)));
  }

  /** 19.2: answerable=false gives insufficient evidence, whatever else the model wrote. */
  @Test
  void anUnanswerableAnswerIsInsufficientEvidence() {
    assertThat(validate(false, "It is 75% [1].", List.of(1), 3)).isEmpty();
  }

  /** 19.2: a factual answer with no valid citation becomes insufficient evidence. */
  @Test
  void anAnswerWithNoValidCitationIsInsufficientEvidence() {
    assertThat(validate(true, "It is 75% [4].", List.of(4, 5), 3)).isEmpty();
    assertThat(validate(true, "It is 75%.", List.of(), 3)).isEmpty();
    assertThat(validate(true, " [1] ", List.of(1), 3)).as("nothing but a marker").isEmpty();
  }

  private static java.util.Optional<Validated> validate(
      boolean answerable, String answer, List<Integer> citations, int sources) {
    return CitationValidator.validate(new ModelAnswer(answerable, answer, citations), sources);
  }
}
