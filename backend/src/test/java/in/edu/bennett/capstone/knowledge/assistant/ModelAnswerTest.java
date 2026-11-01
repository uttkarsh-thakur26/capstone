package in.edu.bennett.capstone.knowledge.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

/** Structured output handling (11.1 step 7; 19.2 "malformed model output is handled"). */
class ModelAnswerTest {

  private final JsonMapper json = JsonMapper.builder().build();

  @Test
  void outputMatchingTheSchemaIsRead() {
    assertThat(
            ModelAnswer.parse(
                json, "{\"answerable\": true, \"answer\": \"75% [1].\", \"citations\": [1, 3]}"))
        .contains(new ModelAnswer(true, "75% [1].", List.of(1, 3)));
    assertThat(
            ModelAnswer.parse(json, "{\"citations\": [], \"answer\": \"\", \"answerable\": false}"))
        .contains(new ModelAnswer(false, "", List.of()));
  }

  /** Anything off the schema is malformed: nothing is coerced or guessed. */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "The minimum attendance is 75% [1].",
        "{\"answerable\": true, \"answer\": \"75% [1].\"",
        "[1, 2]",
        "null",
        "{\"answerable\": true, \"answer\": \"75% [1].\"}",
        "{\"answerable\": true, \"answer\": \"75% [1].\", \"citations\": [1], \"title\": \"X\"}",
        "{\"answerable\": \"true\", \"answer\": \"75% [1].\", \"citations\": [1]}",
        "{\"answerable\": true, \"answer\": 75, \"citations\": [1]}",
        "{\"answerable\": true, \"answer\": \"75% [1].\", \"citations\": [\"1\"]}",
        "{\"answerable\": true, \"answer\": \"75% [1].\", \"citations\": [1.5]}",
        "{\"answerable\": true, \"answer\": \"75% [1].\", \"citations\": [99999999999]}",
        "{\"answerable\": true, \"answer\": null, \"citations\": [1]}",
        "{\"answerable\": true, \"answer\": \"75% [1].\", \"citations\": 1}"
      })
  void malformedOutputIsRejected(String output) {
    assertThat(ModelAnswer.parse(json, output)).isEmpty();
  }
}
