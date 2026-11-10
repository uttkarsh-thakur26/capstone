package in.edu.bennett.capstone.knowledge.common;

import java.time.Duration;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * A request refused by a rate limit (SEC-8): {@code 429} with the code {@code RATE_LIMITED}, a
 * {@code Retry-After} header and {@code retryAfterSeconds} in the body, so the screen can count
 * down to the moment the request will be accepted again.
 */
public class RateLimitedException extends ApiException {

  public static final String RETRY_AFTER_SECONDS = "retryAfterSeconds";

  public RateLimitedException(String what, Duration retryAfter) {
    super(
        HttpStatus.TOO_MANY_REQUESTS,
        "RATE_LIMITED",
        what + " Try again in " + inWords(seconds(retryAfter)) + ".",
        Map.of(RETRY_AFTER_SECONDS, seconds(retryAfter)));
  }

  /** Whole seconds, rounded up, and at least one: "0 seconds" would invite an instant retry. */
  static long seconds(Duration retryAfter) {
    long millis = Math.max(1, retryAfter.toMillis());
    return (millis + 999) / 1000;
  }

  static String inWords(long seconds) {
    if (seconds < 120) {
      return seconds + (seconds == 1 ? " second" : " seconds");
    }
    long minutes = (seconds + 59) / 60;
    if (minutes < 120) {
      return minutes + " minutes";
    }
    return ((minutes + 59) / 60) + " hours";
  }
}
