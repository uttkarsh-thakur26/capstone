package in.edu.bennett.capstone.knowledge.document;

import in.edu.bennett.capstone.knowledge.common.LikePatterns;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * The filters of the document list (DOC-4). They narrow the readable documents and never widen
 * them: the list query always adds the readable predicate as well (ACL-E2).
 *
 * @param text part of the title or description, ignoring case; {@code LIKE} wildcards in it are
 *     matched literally
 * @param category only this category; null for all
 * @param mine only documents the user owns
 * @param archived only archived, or only not archived; null for both
 */
record DocumentQuery(
    String text, DocumentStatus status, DocumentCategory category, boolean mine, Boolean archived) {

  Specification<Document> toSpecification(UUID userId) {
    List<Specification<Document>> filters = new ArrayList<>();
    if (text != null && !text.isBlank()) {
      String pattern = LikePatterns.containing(text);
      filters.add(
          (document, query, cb) ->
              cb.or(
                  cb.like(cb.lower(document.get("title")), pattern, LikePatterns.ESCAPE),
                  cb.like(cb.lower(document.get("description")), pattern, LikePatterns.ESCAPE)));
    }
    if (status != null) {
      filters.add((document, query, cb) -> cb.equal(document.get("status"), status));
    }
    if (category != null) {
      filters.add((document, query, cb) -> cb.equal(document.get("category"), category));
    }
    if (mine) {
      filters.add((document, query, cb) -> cb.equal(document.get("ownerId"), userId));
    }
    if (archived != null) {
      filters.add((document, query, cb) -> cb.equal(document.get("archived"), archived));
    }
    return Specification.allOf(filters);
  }
}
