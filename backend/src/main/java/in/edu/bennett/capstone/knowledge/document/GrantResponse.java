package in.edu.bennett.capstone.knowledge.document;

import in.edu.bennett.capstone.knowledge.user.Role;
import in.edu.bennett.capstone.knowledge.user.UserSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/** One grant as the owner and admins see it: the role, or the person with their name and email. */
public record GrantResponse(
    @Schema(requiredMode = RequiredMode.REQUIRED) GranteeType type,
    @Schema(description = "Set for a ROLE grant.") Role role,
    @Schema(description = "Set for a USER grant.") UserSummary user) {}
