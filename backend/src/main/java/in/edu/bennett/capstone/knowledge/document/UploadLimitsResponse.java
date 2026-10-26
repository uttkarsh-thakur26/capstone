package in.edu.bennett.capstone.knowledge.document;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;

/** What the server accepts, so the interface checks the same limits before sending (UI-4). */
public record UploadLimitsResponse(
    @Schema(requiredMode = RequiredMode.REQUIRED) long maxFileSizeBytes,
    @Schema(requiredMode = RequiredMode.REQUIRED) List<String> contentTypes) {}
