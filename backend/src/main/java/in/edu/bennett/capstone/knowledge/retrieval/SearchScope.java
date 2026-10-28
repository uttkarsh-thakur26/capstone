package in.edu.bennett.capstone.knowledge.retrieval;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * The optional filters of a search (specification 10). They narrow the retrievable chunks and never
 * widen them: the search statement always applies the retrievable predicate as well (ACL-E2).
 *
 * @param documentIds only these documents; empty for all
 * @param category only this category, already lower-cased; null for all
 */
record SearchScope(Set<UUID> documentIds, String category) {

  static SearchScope of(List<UUID> documentIds, String category) {
    return new SearchScope(
        documentIds == null ? Set.of() : Set.copyOf(documentIds),
        category == null || category.isBlank() ? null : category.strip().toLowerCase(Locale.ROOT));
  }
}
