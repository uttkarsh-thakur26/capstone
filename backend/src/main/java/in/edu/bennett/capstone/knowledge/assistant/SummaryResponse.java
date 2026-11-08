package in.edu.bennett.capstone.knowledge.assistant;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The answer of {@code GET} and {@code POST /documents/{id}/summary} (15.2, SUM-5). Sent only after
 * a fresh check that the user can read the document (ACL-E5).
 *
 * @param status {@code NONE} when no summary of the document as it is indexed now exists
 * @param summary the summary when {@code READY}, otherwise null
 * @param model the chat model the summary is kept under
 * @param path {@code SINGLE_PASS} or {@code MAP_REDUCE} once ready
 * @param modelCalls how many calls the summary took, the reduce included
 * @param finishedAt when it was finished or failed
 * @param failureMessage why it failed, in words a reader can see
 */
public record SummaryResponse(
    @Schema(requiredMode = RequiredMode.REQUIRED) UUID documentId,
    @Schema(requiredMode = RequiredMode.REQUIRED) Status status,
    Content summary,
    String model,
    String promptVersion,
    String path,
    Integer modelCalls,
    Instant finishedAt,
    String failureMessage) {

  public enum Status {
    NONE,
    PENDING,
    READY,
    FAILED
  }

  /**
   * The summary (SUM-2). Every item ends with {@code [n]} markers that name its {@code citations};
   * an overview with no supported sentence is null.
   */
  public record Content(
      String overview,
      @Schema(requiredMode = RequiredMode.REQUIRED) List<String> keyPoints,
      @Schema(requiredMode = RequiredMode.REQUIRED) List<String> rules,
      @Schema(requiredMode = RequiredMode.REQUIRED) List<String> dates,
      @Schema(requiredMode = RequiredMode.REQUIRED) List<SourceResponse> citations) {}

  static SummaryResponse none(UUID documentId) {
    return new SummaryResponse(documentId, Status.NONE, null, null, null, null, null, null, null);
  }
}
