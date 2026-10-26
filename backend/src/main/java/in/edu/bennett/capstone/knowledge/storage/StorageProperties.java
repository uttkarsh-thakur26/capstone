package in.edu.bennett.capstone.knowledge.storage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * File storage settings (specification 18, {@code app.storage}).
 *
 * @param root the directory that holds the files. Required: an empty value would otherwise mean the
 *     working directory.
 * @param cleanupInterval how often deletions that failed are tried again (DOC-9)
 */
@Validated
@ConfigurationProperties("app.storage")
public record StorageProperties(
    @NotBlank String root, @DefaultValue("10m") @NotNull Duration cleanupInterval) {}
