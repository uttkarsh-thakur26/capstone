package in.edu.bennett.capstone.knowledge.retrieval;

import in.edu.bennett.capstone.knowledge.document.DocumentCategory;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The optional filters of a search (specification 10). They narrow the retrievable chunks and never
 * widen them: the search statement always applies the retrievable predicate as well (ACL-E2).
 *
 * @param documentIds only these documents; empty for all
 * @param category only this category; null for all
 */
record SearchScope(Set<UUID> documentIds, DocumentCategory category) {

  static SearchScope of(List<UUID> documentIds, DocumentCategory category) {
    return new SearchScope(documentIds == null ? Set.of() : Set.copyOf(documentIds), category);
  }
}
