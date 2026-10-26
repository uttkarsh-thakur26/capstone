package in.edu.bennett.capstone.knowledge.document;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Upload settings (specification 18, {@code app.upload}). There is no allowed-types setting: PDF is
 * the only type the content check knows (D9, ADR-029).
 *
 * @param maxFileSize the largest file accepted (DOC-1). The multipart limits follow it.
 */
@Validated
@ConfigurationProperties("app.upload")
public record UploadProperties(@DefaultValue("25MB") @NotNull DataSize maxFileSize) {

  @Schema(hidden = true)
  @AssertTrue(message = "must be larger than zero")
  public boolean isMaxFileSizePositive() {
    return maxFileSize == null || maxFileSize.toBytes() > 0;
  }
}
