package in.edu.bennett.capstone.knowledge.security;

import in.edu.bennett.capstone.knowledge.common.RateLimitedException;
import in.edu.bennett.capstone.knowledge.security.RateLimitProperties.Limit;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Every rate limit of SEC-8 and AUTH-5 (ADR-041), in memory, which SEC-8 allows for one instance.
 * Each limit is a token bucket per key: it holds at most {@code requests} tokens, a request takes
 * one, and tokens come back evenly over the period. A refused request learns exactly when the next
 * token arrives, which is the "try again in N seconds" the screen shows.
 *
 * <p>Time comes from the injected {@link Clock} (4.5), so tests move it instead of sleeping. The
 * state is lost on a restart, which only gives everyone a full bucket again.
 */
@Component
public class RateLimiter {

  /** Above this many buckets, the full ones are dropped: they hold nothing a new one would not. */
  static final int SWEEP_ABOVE = 10_000;

  private final Clock clock;
  private final RateLimitProperties limits;
  private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<UUID, Day> days = new ConcurrentHashMap<>();

  RateLimiter(Clock clock, RateLimitProperties limits) {
    this.clock = clock;
    this.limits = limits;
  }

  /**
   * An AI request (ask, summarize, compare) of this user: the daily cap first, then the per-minute
   * budget. A request refused by the budget does not count against the day.
   */
  public void checkAi(UUID userId) {
    LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
    boolean[] withinDay = {false};
    days.compute(
        userId,
        (id, day) -> {
          Day current = day == null || !day.date().equals(today) ? new Day(today, 0) : day;
          if (current.count() >= limits.aiDaily()) {
            return current;
          }
          withinDay[0] = true;
          return new Day(today, current.count() + 1);
        });
    if (!withinDay[0]) {
      Duration untilTomorrow =
          Duration.between(
              clock.instant(), today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
      throw new RateLimitedException(
          "You have made today's " + limits.aiDaily() + " requests to the assistant.",
          untilTomorrow);
    }
    Optional<Duration> wait = take("ai:" + userId, limits.ai());
    if (wait.isPresent()) {
      days.computeIfPresent(userId, (id, day) -> new Day(day.date(), Math.max(0, day.count() - 1)));
      throw new RateLimitedException("Too many requests.", wait.get());
    }
    sweep();
  }

  /** A registration from this address; nothing is limited when there is no address. */
  public void checkRegistration(String address) {
    if (address == null) {
      return;
    }
    take("register:" + address, limits.registration())
        .ifPresent(
            wait -> {
              throw new RateLimitedException("Too many registrations from this network.", wait);
            });
    sweep();
  }

  /**
   * Before a password is checked (AUTH-5): takes one token for the email and one for the address,
   * so even many attempts at the same moment cannot exceed the limit. A successful sign-in gives
   * both back with {@link #signInSucceeded}, so only failures count. Unknown emails are limited
   * exactly like known ones, so a 429 says nothing about whether an account exists.
   *
   * <p>The email's count is kept per address: failures from elsewhere never lock the owner out, so
   * nobody can keep an account, such as the only admin, locked by typing wrong passwords for it.
   * The address's own count still limits guessing from any one place (review of ADR-041).
   */
  void beforeSignIn(String email, String address) {
    String emailKey = emailKey(email, address);
    Optional<Duration> emailWait = take(emailKey, limits.loginPerEmail());
    if (emailWait.isPresent()) {
      throw new RateLimitedException("Too many failed sign-ins.", emailWait.get());
    }
    if (address != null) {
      Optional<Duration> addressWait = take("login-address:" + address, limits.loginPerAddress());
      if (addressWait.isPresent()) {
        giveBack(emailKey, limits.loginPerEmail());
        throw new RateLimitedException("Too many failed sign-ins.", addressWait.get());
      }
    }
    sweep();
  }

  void signInSucceeded(String email, String address) {
    giveBack(emailKey(email, address), limits.loginPerEmail());
    if (address != null) {
      giveBack("login-address:" + address, limits.loginPerAddress());
    }
  }

  private static String emailKey(String email, String address) {
    return "login-email:" + email + (address == null ? "" : "|" + address);
  }

  /** Takes a token, or says how long until the next one. */
  private Optional<Duration> take(String key, Limit limit) {
    long now = clock.millis();
    long[] waitMillis = {0};
    buckets.compute(
        key,
        (k, bucket) -> {
          Bucket current = refilled(bucket, limit, now);
          if (current.tokens() >= 1) {
            return new Bucket(current.tokens() - 1, now);
          }
          // Multiplied, not divided by the rate, and with a tolerance for the last binary digit:
          // otherwise an exact 1,800,000 ms comes out as 1,800,001 and "30 minutes" as 31.
          double exact = (1 - current.tokens()) * limit.period().toMillis() / limit.requests();
          waitMillis[0] = Math.max(1, (long) Math.ceil(exact - 1e-6));
          return current;
        });
    return waitMillis[0] == 0 ? Optional.empty() : Optional.of(Duration.ofMillis(waitMillis[0]));
  }

  private void giveBack(String key, Limit limit) {
    long now = clock.millis();
    buckets.computeIfPresent(
        key,
        (k, bucket) -> {
          Bucket current = refilled(bucket, limit, now);
          return new Bucket(Math.min(limit.requests(), current.tokens() + 1), now);
        });
  }

  private static Bucket refilled(Bucket bucket, Limit limit, long now) {
    if (bucket == null) {
      return new Bucket(limit.requests(), now);
    }
    double tokens = bucket.tokens() + (now - bucket.updatedAt()) * ratePerMilli(limit);
    return new Bucket(Math.min(limit.requests(), tokens), now);
  }

  private static double ratePerMilli(Limit limit) {
    return (double) limit.requests() / limit.period().toMillis();
  }

  /** Keeps the memory bounded when many addresses come and go. */
  private void sweep() {
    if (buckets.size() <= SWEEP_ABOVE) {
      return;
    }
    long now = clock.millis();
    buckets.entrySet().removeIf(entry -> isFull(entry.getKey(), entry.getValue(), now));
    LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
    days.values().removeIf(day -> day.date().isBefore(today));
  }

  private boolean isFull(String key, Bucket bucket, long now) {
    Limit limit = limitOf(key);
    return refilled(bucket, limit, now).tokens() >= limit.requests();
  }

  private Limit limitOf(String key) {
    if (key.startsWith("ai:")) {
      return limits.ai();
    }
    if (key.startsWith("register:")) {
      return limits.registration();
    }
    if (key.startsWith("login-email:")) {
      return limits.loginPerEmail();
    }
    return limits.loginPerAddress();
  }

  /** How many buckets are kept, for the test of the sweep. */
  int size() {
    return buckets.size();
  }

  private record Bucket(double tokens, long updatedAt) {}

  private record Day(LocalDate date, int count) {}
}
