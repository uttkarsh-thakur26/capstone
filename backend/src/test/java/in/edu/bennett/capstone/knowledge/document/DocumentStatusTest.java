package in.edu.bennett.capstone.knowledge.document;

import static in.edu.bennett.capstone.knowledge.document.DocumentStatus.FAILED;
import static in.edu.bennett.capstone.knowledge.document.DocumentStatus.INDEXED;
import static in.edu.bennett.capstone.knowledge.document.DocumentStatus.PROCESSING;
import static in.edu.bennett.capstone.knowledge.document.DocumentStatus.UPLOADED;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Specification 8.2 with ADR-030: every pair of statuses, legal or not. */
class DocumentStatusTest {

  private static final Map<DocumentStatus, Set<DocumentStatus>> LEGAL =
      Map.of(
          UPLOADED, Set.of(PROCESSING),
          PROCESSING, Set.of(INDEXED, FAILED, UPLOADED),
          INDEXED, Set.of(UPLOADED),
          FAILED, Set.of(UPLOADED));

  @Test
  void onlyTheTransitionsOfTheStatusMachineAreLegal() {
    for (DocumentStatus from : DocumentStatus.values()) {
      for (DocumentStatus to : DocumentStatus.values()) {
        assertThat(from.canBecome(to))
            .as("%s -> %s", from, to)
            .isEqualTo(LEGAL.get(from).contains(to));
      }
    }
  }

  @Test
  void onlyAFinishedRunCanBeReprocessed() {
    assertThat(INDEXED.canBeReprocessed()).isTrue();
    assertThat(FAILED.canBeReprocessed()).isTrue();
    assertThat(UPLOADED.canBeReprocessed()).isFalse();
    assertThat(PROCESSING.canBeReprocessed()).isFalse();
  }
}
