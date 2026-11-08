package in.edu.bennett.capstone.knowledge.assistant;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;
import java.util.UUID;

/**
 * The answer of {@code POST /documents/compare} (CMP-3, CMP-4). Every difference quotes the text it
 * rests on, found in the stored chunks, with its pages on each side where it has one; a side
 * without a quote is a difference present on the other side only.
 *
 * @param passagesCompared how many pairs or one-sided passages went to the model; identical
 *     passages never do
 * @param identicalPassages how many passages read the same in both documents
 * @param modelCalls 0 when the documents read the same, otherwise 1
 * @param differencesDropped how many differences the model reported that failed the checks
 */
public record ComparisonResponse(
    @Schema(requiredMode = RequiredMode.REQUIRED) DocumentRef left,
    @Schema(requiredMode = RequiredMode.REQUIRED) DocumentRef right,
    @Schema(requiredMode = RequiredMode.REQUIRED) List<Difference> differences,
    @Schema(requiredMode = RequiredMode.REQUIRED) int passagesCompared,
    @Schema(requiredMode = RequiredMode.REQUIRED) int identicalPassages,
    @Schema(requiredMode = RequiredMode.REQUIRED) int differencesDropped,
    @Schema(requiredMode = RequiredMode.REQUIRED) int modelCalls,
    String model,
    String promptVersion) {

  public record DocumentRef(
      @Schema(requiredMode = RequiredMode.REQUIRED) UUID id,
      @Schema(requiredMode = RequiredMode.REQUIRED) String title) {}

  /** CMP-3. */
  public enum Category {
    ADDED,
    REMOVED,
    CHANGED_REQUIREMENT,
    CHANGED_DATE,
    CHANGED_THRESHOLD,
    OTHER
  }

  /**
   * One difference.
   *
   * @param explanation one sentence by the model; every number in it is in the quotes
   * @param left the left document's side, null when the difference is in the right one only
   * @param right the right document's side, null when it is in the left one only
   */
  public record Difference(
      @Schema(requiredMode = RequiredMode.REQUIRED) Category category,
      @Schema(requiredMode = RequiredMode.REQUIRED) String explanation,
      Side left,
      Side right) {}

  /**
   * @param quote the document's own text, as stored, that the difference rests on
   * @param citation its document and pages, in the shape of an answer's citation
   */
  public record Side(
      @Schema(requiredMode = RequiredMode.REQUIRED) String quote,
      @Schema(requiredMode = RequiredMode.REQUIRED) SourceResponse citation) {}
}
