package in.edu.bennett.capstone.knowledge.common;

/** Public facts about the running application, served by {@code GET /api/v1/meta}. */
public record MetaResponse(String name, String version) {}
