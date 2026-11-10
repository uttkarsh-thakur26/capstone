package in.edu.bennett.capstone.knowledge.security;

import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Rate limits (SEC-8, AUTH-5, ADR-041), {@code app.security.rate-limits}. Each limit is a bucket of
 * so many requests that refills evenly over its period: after a burst, one more request is allowed
 * every {@code period / requests}. Every value is an environment variable too (.env.example), so it
 * can be raised on a demonstration day without a rebuild.
 *
 * @param aiRequests asking, summarizing and comparing together, per user, in {@code aiPeriod}
 * @param aiDaily the most AI requests one user may make in a UTC day
 * @param loginEmailFailures failed sign-ins for one email from one address, in {@code
 *     loginEmailPeriod}
 * @param loginAddressFailures failed sign-ins from one address, for any email, in {@code
 *     loginAddressPeriod}
 * @param registrationRequests registrations from one address in {@code registrationPeriod}; a class
 *     behind one campus address shares it
 */
@Validated
@ConfigurationProperties("app.security.rate-limits")
public record RateLimitProperties(
    @DefaultValue("10") @Positive int aiRequests,
    @DefaultValue("1m") Duration aiPeriod,
    @DefaultValue("200") @Positive int aiDaily,
    @DefaultValue("5") @Positive int loginEmailFailures,
    @DefaultValue("15m") Duration loginEmailPeriod,
    @DefaultValue("20") @Positive int loginAddressFailures,
    @DefaultValue("15m") Duration loginAddressPeriod,
    @DefaultValue("50") @Positive int registrationRequests,
    @DefaultValue("1h") Duration registrationPeriod) {

  public RateLimitProperties {
    for (Duration period :
        new Duration[] {aiPeriod, loginEmailPeriod, loginAddressPeriod, registrationPeriod}) {
      if (period == null || period.isZero() || period.isNegative()) {
        throw new IllegalArgumentException("A rate limit's period must be positive, not " + period);
      }
    }
  }

  Limit ai() {
    return new Limit(aiRequests, aiPeriod);
  }

  Limit loginPerEmail() {
    return new Limit(loginEmailFailures, loginEmailPeriod);
  }

  Limit loginPerAddress() {
    return new Limit(loginAddressFailures, loginAddressPeriod);
  }

  Limit registration() {
    return new Limit(registrationRequests, registrationPeriod);
  }

  /** At most {@code requests} in a burst, refilled evenly over {@code period}. */
  record Limit(int requests, Duration period) {}
}
