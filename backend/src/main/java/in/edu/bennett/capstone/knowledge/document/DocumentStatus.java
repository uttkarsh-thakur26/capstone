package in.edu.bennett.capstone.knowledge.document;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The status machine of specification 8.2 (ADR-030). Stored as text, checked by a constraint. Only
 * {@code INDEXED} documents are retrievable (ACL-4).
 */
public enum DocumentStatus {
  UPLOADED,
  PROCESSING,
  INDEXED,
  FAILED;

  /**
   * Every legal transition. {@code PROCESSING -> UPLOADED} is the stuck-job recovery of ING-2 and is
   * never offered through the API; reprocess starts from {@code FAILED} or {@code INDEXED} only.
   */
  private static final Map<DocumentStatus, Set<DocumentStatus>> NEXT =
      Map.of(
          UPLOADED, EnumSet.of(PROCESSING),
          PROCESSING, EnumSet.of(INDEXED, FAILED, UPLOADED),
          INDEXED, EnumSet.of(UPLOADED),
          FAILED, EnumSet.of(UPLOADED));

  public boolean canBecome(DocumentStatus next) {
    return NEXT.get(this).contains(next);
  }

  /** DOC-10: a person may send a document back for processing only once its last run ended. */
  public boolean canBeReprocessed() {
    return this == INDEXED || this == FAILED;
  }
}
