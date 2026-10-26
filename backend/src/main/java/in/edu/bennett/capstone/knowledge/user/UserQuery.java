package in.edu.bennett.capstone.knowledge.user;

import in.edu.bennett.capstone.knowledge.common.LikePatterns;
import jakarta.persistence.criteria.Expression;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * Filters of the admin user list. {@code text} matches part of the email or the display name,
 * ignoring case; the wildcard characters of SQL {@code LIKE} in it are matched literally.
 */
record UserQuery(String text, Role role, Boolean enabled) {

  Specification<UserAccount> toSpecification() {
    List<Specification<UserAccount>> filters = new ArrayList<>();
    if (text != null && !text.isBlank()) {
      String pattern = LikePatterns.containing(text);
      filters.add(
          (root, query, cb) -> {
            Expression<String> email = cb.lower(root.get("email"));
            Expression<String> name = cb.lower(root.get("displayName"));
            return cb.or(
                cb.like(email, pattern, LikePatterns.ESCAPE),
                cb.like(name, pattern, LikePatterns.ESCAPE));
          });
    }
    if (role != null) {
      filters.add((root, query, cb) -> cb.equal(root.get("role"), role));
    }
    if (enabled != null) {
      filters.add((root, query, cb) -> cb.equal(root.get("enabled"), enabled));
    }
    return Specification.allOf(filters);
  }
}
