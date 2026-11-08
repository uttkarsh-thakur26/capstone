package in.edu.bennett.capstone.knowledge.assistant;

import in.edu.bennett.capstone.knowledge.assistant.Answer.Usage;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * What the chat model costs, in US dollars per million tokens ({@code app.assistant.price}), so the
 * cost of each summary and comparison is logged in money as well as tokens (ADR-040). The defaults
 * are {@code gpt-6-luna}'s (ADR-036); another model needs its own prices here.
 *
 * @param input a prompt token
 * @param cachedInput a prompt token served from the provider's cache
 * @param output a completion token, reasoning included
 */
@Validated
@ConfigurationProperties("app.assistant.price")
public record ModelPrices(
    @DefaultValue("0.10") @DecimalMin("0") BigDecimal input,
    @DefaultValue("0.01") @DecimalMin("0") BigDecimal cachedInput,
    @DefaultValue("0.50") @DecimalMin("0") BigDecimal output) {

  private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

  /** The cost of these tokens, rounded to a millionth of a dollar; unknown counts count as 0. */
  BigDecimal cost(Usage usage) {
    long in = usage.inputTokens() == null ? 0 : usage.inputTokens();
    long cached = usage.cachedInputTokens() == null ? 0 : Math.min(usage.cachedInputTokens(), in);
    long out = usage.outputTokens() == null ? 0 : usage.outputTokens();
    BigDecimal total =
        input
            .multiply(BigDecimal.valueOf(in - cached))
            .add(cachedInput.multiply(BigDecimal.valueOf(cached)))
            .add(output.multiply(BigDecimal.valueOf(out)));
    return total.divide(MILLION, 6, RoundingMode.HALF_UP);
  }

  /** Two calls' tokens together; an unknown count stays unknown only if both are. */
  static Usage add(Usage a, Usage b) {
    if (a == null) {
      return b;
    }
    if (b == null) {
      return a;
    }
    return new Usage(
        sum(a.inputTokens(), b.inputTokens()),
        sum(a.outputTokens(), b.outputTokens()),
        a.cachedInputTokens() == null && b.cachedInputTokens() == null
            ? null
            : (a.cachedInputTokens() == null ? 0 : a.cachedInputTokens())
                + (b.cachedInputTokens() == null ? 0 : b.cachedInputTokens()));
  }

  private static Integer sum(Integer a, Integer b) {
    if (a == null && b == null) {
      return null;
    }
    return (a == null ? 0 : a) + (b == null ? 0 : b);
  }
}
