package in.edu.bennett.capstone.knowledge.common;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

/**
 * The list envelope of specification 15.1: {@code { items, page, size, totalItems, totalPages }}.
 */
public record PageResponse<T>(
    @Schema(requiredMode = RequiredMode.REQUIRED) List<T> items,
    @Schema(requiredMode = RequiredMode.REQUIRED) int page,
    @Schema(requiredMode = RequiredMode.REQUIRED) int size,
    @Schema(requiredMode = RequiredMode.REQUIRED) long totalItems,
    @Schema(requiredMode = RequiredMode.REQUIRED) int totalPages) {

  public static final int MAX_SIZE = 100;

  /**
   * The last page a list may ask for: beyond it, a full page would start past the largest row
   * offset a query can take, which would fail as a 500 instead of a 400 (15.1).
   */
  public static final int MAX_PAGE = Integer.MAX_VALUE / MAX_SIZE;

  public static <T> PageResponse<T> of(Page<T> page) {
    return new PageResponse<>(
        page.getContent(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }

  /**
   * Builds the page request for a list endpoint. {@code sort} is {@code field} or {@code
   * field,asc|desc}, and only fields on the endpoint's allow-list are accepted, so a client can
   * never sort by an arbitrary column (SEC-7).
   *
   * @param sortable API field name to entity property
   */
  public static PageRequest request(
      int page, int size, String sort, Map<String, String> sortable, Sort fallback) {
    if (sort == null || sort.isBlank()) {
      return PageRequest.of(page, size, fallback);
    }
    String[] parts = sort.split(",", -1);
    String property = sortable.get(parts[0].strip());
    String direction = parts.length > 1 ? parts[1].strip().toLowerCase(Locale.ROOT) : "asc";
    if (property == null
        || parts.length > 2
        || !(direction.equals("asc") || direction.equals("desc"))) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "INVALID_SORT",
          "sort must be one of " + sortable.keySet() + ", optionally followed by ,asc or ,desc.");
    }
    return PageRequest.of(
        page, size, Sort.by(Sort.Direction.fromString(direction), property).and(fallback));
  }
}
