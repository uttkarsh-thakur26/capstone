package in.edu.bennett.capstone.knowledge.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import in.edu.bennett.capstone.knowledge.common.RateLimitedException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The token buckets of SEC-8 and AUTH-5 (ADR-041), with a clock the test moves: bursts, the even
 * refill, the exact wait reported, the daily cap and its reset at UTC midnight, sign-in failures
 * only, and the sweep that keeps memory bounded.
 */
class RateLimiterTest {

  private static final RateLimitProperties LIMITS =
      new RateLimitProperties(
          3,
          Duration.ofMinutes(1),
          5,
          2,
          Duration.ofMinutes(10),
          4,
          Duration.ofMinutes(10),
          2,
          Duration.ofHours(1));

  private final MovableClock clock = new MovableClock(Instant.parse("2026-11-09T10:00:00Z"));
  private final RateLimiter limiter = new RateLimiter(clock, LIMITS);
  private final UUID user = UUID.randomUUID();

  @Test
  void aBurstIsAllowedThenOneRequestPerRefill() {
    for (int i = 0; i < 3; i++) {
      limiter.checkAi(user);
    }

    RateLimitedException refused = refusedAi(user);
    // Three a minute: a token every 20 seconds.
    assertThat(refused.properties().get("retryAfterSeconds")).isEqualTo(20L);
    assertThat(refused.getMessage()).isEqualTo("Too many requests. Try again in 20 seconds.");

    clock.advance(Duration.ofSeconds(19));
    assertThat(refusedAi(user).properties().get("retryAfterSeconds")).isEqualTo(1L);
    clock.advance(Duration.ofSeconds(1));
    assertThatNoException().isThrownBy(() -> limiter.checkAi(user));
    assertThat(refusedAi(user)).isNotNull();
  }

  @Test
  void eachUserHasABudgetOfTheirOwn() {
    for (int i = 0; i < 3; i++) {
      limiter.checkAi(user);
    }
    assertThat(refusedAi(user)).isNotNull();

    assertThatNoException().isThrownBy(() -> limiter.checkAi(UUID.randomUUID()));
  }

  @Test
  void theDailyCapHoldsUntilMidnightUtcAndRefusedRequestsDoNotCount() {
    for (int i = 0; i < 3; i++) {
      limiter.checkAi(user);
    }
    // Refused by the per-minute budget: must not use up the day.
    assertThat(refusedAi(user)).isNotNull();
    clock.advance(Duration.ofMinutes(1));
    limiter.checkAi(user);
    limiter.checkAi(user);

    RateLimitedException daily = refusedAi(user);
    assertThat(daily.getMessage())
        .isEqualTo("You have made today's 5 requests to the assistant. Try again in 14 hours.");
    // From 10:01 to midnight UTC.
    assertThat(daily.properties().get("retryAfterSeconds")).isEqualTo(13 * 3600L + 59 * 60);

    clock.set(Instant.parse("2026-11-10T00:00:00Z"));
    assertThatNoException().isThrownBy(() -> limiter.checkAi(user));
  }

  @Test
  void onlyFailedSignInsCount() {
    for (int i = 0; i < 5; i++) {
      limiter.beforeSignIn("a@example.org", "10.0.0.1");
      limiter.signInSucceeded("a@example.org", "10.0.0.1");
    }
    limiter.beforeSignIn("a@example.org", "10.0.0.1");
    limiter.beforeSignIn("a@example.org", "10.0.0.1");

    RateLimitedException refused =
        catchThrowableOfType(
            RateLimitedException.class, () -> limiter.beforeSignIn("a@example.org", "10.0.0.1"));
    assertThat(refused.getMessage()).startsWith("Too many failed sign-ins.");
    // Two in ten minutes: one every five.
    assertThat(refused.properties().get("retryAfterSeconds")).isEqualTo(300L);
  }

  /** Nobody can lock someone else out: failures elsewhere do not count against the owner. */
  @Test
  void failuresFromAnotherAddressDoNotLockTheOwnerOut() {
    limiter.beforeSignIn("admin@example.org", "203.0.113.7");
    limiter.beforeSignIn("admin@example.org", "203.0.113.7");
    assertThat(
            catchThrowableOfType(
                RateLimitedException.class,
                () -> limiter.beforeSignIn("admin@example.org", "203.0.113.7")))
        .isNotNull();

    assertThatNoException().isThrownBy(() -> limiter.beforeSignIn("admin@example.org", "10.0.0.1"));
  }

  @Test
  void anAddressIsLimitedAcrossEmailsAndARefusalGivesTheEmailItsTokenBack() {
    for (int i = 0; i < 4; i++) {
      limiter.beforeSignIn("user" + i + "@example.org", "10.0.0.9");
    }

    assertThat(
            catchThrowableOfType(
                RateLimitedException.class,
                () -> limiter.beforeSignIn("victim@example.org", "10.0.0.9")))
        .isNotNull();
    // The refusal by address did not use up the victim's own budget.
    limiter.beforeSignIn("victim@example.org", "10.0.0.10");
    limiter.beforeSignIn("victim@example.org", "10.0.0.11");
  }

  @Test
  void registrationIsLimitedPerAddress() {
    limiter.checkRegistration("10.1.1.1");
    limiter.checkRegistration("10.1.1.1");

    RateLimitedException refused =
        catchThrowableOfType(
            RateLimitedException.class, () -> limiter.checkRegistration("10.1.1.1"));
    assertThat(refused.getMessage())
        .isEqualTo("Too many registrations from this network. Try again in 30 minutes.");
    assertThatNoException().isThrownBy(() -> limiter.checkRegistration("10.1.1.2"));
  }

  @Test
  void fullBucketsAreDroppedOnceThereAreMany() {
    for (int i = 0; i <= RateLimiter.SWEEP_ABOVE; i++) {
      limiter.checkRegistration("10.2." + (i / 256) + "." + (i % 256));
    }
    assertThat(limiter.size()).isGreaterThan(RateLimiter.SWEEP_ABOVE);

    clock.advance(Duration.ofHours(1));
    limiter.checkRegistration("10.3.0.1");

    assertThat(limiter.size()).isEqualTo(1);
  }

  private RateLimitedException refusedAi(UUID id) {
    return catchThrowableOfType(RateLimitedException.class, () -> limiter.checkAi(id));
  }

  /** A clock the test sets and moves. */
  private static final class MovableClock extends Clock {

    private Instant now;

    MovableClock(Instant now) {
      this.now = now;
    }

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    void set(Instant instant) {
      now = instant;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      MovableClock self = this;
      return new Clock() {
        @Override
        public ZoneId getZone() {
          return zone;
        }

        @Override
        public Clock withZone(ZoneId other) {
          return self.withZone(other);
        }

        @Override
        public Instant instant() {
          return self.now;
        }
      };
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
