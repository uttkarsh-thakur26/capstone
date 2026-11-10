package in.edu.bennett.capstone.knowledge.analytics;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /admin/analytics} (15.2, 14.3): admins only, by the URL rule on {@code
 * /api/v1/admin/**} and by method security on {@link AnalyticsService}.
 */
@RestController
class AnalyticsController {

  private final AnalyticsService analytics;

  AnalyticsController(AnalyticsService analytics) {
    this.analytics = analytics;
  }

  /**
   * @param days calendar days, today included
   * @param zone the reader's time zone, so that days are the reader's days
   */
  @GetMapping(path = "/api/v1/admin/analytics", produces = MediaType.APPLICATION_JSON_VALUE)
  AnalyticsResponse analytics(
      @RequestParam(defaultValue = "30") @Min(1) @Max(AnalyticsService.MAX_DAYS) int days,
      @RequestParam(defaultValue = "UTC") @Size(max = 64) String zone) {
    return analytics.analytics(days, zone);
  }
}
