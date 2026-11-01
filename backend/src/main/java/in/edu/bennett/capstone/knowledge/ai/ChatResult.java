package in.edu.bennett.capstone.knowledge.ai;

/**
 * What the chat model returned for one request, with the token counts the provider billed.
 *
 * @param text the model's output, empty when it gave none
 * @param model the model that answered, as the provider names it
 * @param inputTokens prompt tokens, null when the provider reports none
 * @param outputTokens completion tokens, reasoning included, null when the provider reports none
 * @param cachedInputTokens prompt tokens served from the provider's cache, null when not reported
 */
public record ChatResult(
    String text, String model, Integer inputTokens, Integer outputTokens, Long cachedInputTokens) {}
