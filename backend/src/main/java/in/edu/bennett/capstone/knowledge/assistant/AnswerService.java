package in.edu.bennett.capstone.knowledge.assistant;

import in.edu.bennett.capstone.knowledge.ai.ChatFailedException;
import in.edu.bennett.capstone.knowledge.ai.ChatGenerator;
import in.edu.bennett.capstone.knowledge.ai.ChatResult;
import in.edu.bennett.capstone.knowledge.ai.EmbeddingFailedException;
import in.edu.bennett.capstone.knowledge.assistant.Answer.Citation;
import in.edu.bennett.capstone.knowledge.assistant.Answer.Trace;
import in.edu.bennett.capstone.knowledge.assistant.CitationValidator.Validated;
import in.edu.bennett.capstone.knowledge.assistant.PromptAssembler.AssembledPrompt;
import in.edu.bennett.capstone.knowledge.retrieval.RetrievalProperties;
import in.edu.bennett.capstone.knowledge.retrieval.RetrievedChunk;
import in.edu.bennett.capstone.knowledge.retrieval.SearchService;
import in.edu.bennett.capstone.knowledge.security.CurrentUser;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * Grounded question answering, steps 4 to 8 of specification 11.1. Deliberately not transactional:
 * retrieval runs its own short read transactions, and the model calls run with no transaction or
 * connection held (4.5).
 *
 * <ol>
 *   <li>Retrieve the user's retrievable chunks for the question (RET-1, through {@link
 *       SearchService#retrieve}, which embeds the question with the query prefix).
 *   <li>Evidence gate: no chunk, or the best one below the similarity floor, is insufficient
 *       evidence, and the chat model is not called.
 *   <li>Assemble the prompt from the retrieved chunks only (RAG-1).
 *   <li>Generate structured output.
 *   <li>Keep only citations of sources that were sent, and fill their titles, pages and snippets
 *       from the retrieved chunks, never from the model (RAG-3).
 * </ol>
 */
@Service
public class AnswerService {

  private final SearchService search;
  private final ChatGenerator generator;
  private final SystemPrompt systemPrompt;
  private final PromptAssembler assembler;
  private final AssistantProperties properties;
  private final RetrievalProperties retrieval;
  private final JsonMapper json;

  AnswerService(
      SearchService search,
      ChatGenerator generator,
      SystemPrompt systemPrompt,
      PromptAssembler assembler,
      AssistantProperties properties,
      RetrievalProperties retrieval,
      JsonMapper json) {
    this.search = search;
    this.generator = generator;
    this.systemPrompt = systemPrompt;
    this.assembler = assembler;
    this.properties = properties;
    this.retrieval = retrieval;
    this.json = json;
  }

  /**
   * @param documentIds restrict retrieval to these documents (CONV-7); null or empty for all
   * @throws AnswerFailedException when a model fails or the output is malformed
   */
  public Answer answer(CurrentUser user, String question, List<UUID> documentIds) {
    long started = System.nanoTime();
    List<RetrievedChunk> chunks;
    try {
      chunks = search.retrieve(user, question, documentIds);
    } catch (EmbeddingFailedException e) {
      throw new AnswerFailedException(
          "The question could not be embedded",
          new Trace(0, null, 0, null, null, null, null, null, millisSince(started), 0),
          e);
    }
    long retrievalMillis = millisSince(started);
    Double top = chunks.isEmpty() ? null : chunks.getFirst().similarity();
    if (top == null || top < retrieval.similarityFloor()) {
      return abstention(
          new Trace(chunks.size(), top, 0, null, null, null, null, null, retrievalMillis, 0));
    }
    AssembledPrompt prompt = assembler.assemble(chunks, question);
    if (prompt.sources().isEmpty()) {
      return abstention(
          new Trace(chunks.size(), top, 0, null, null, null, null, null, retrievalMillis, 0));
    }

    long generating = System.nanoTime();
    ChatResult result;
    try {
      result = generator.generate(systemPrompt.text(), prompt.userMessage(), ModelAnswer.SCHEMA);
    } catch (ChatFailedException e) {
      throw new AnswerFailedException(
          "The chat model gave no answer",
          new Trace(
              chunks.size(),
              top,
              prompt.sources().size(),
              null,
              systemPrompt.version(),
              null,
              null,
              null,
              retrievalMillis,
              millisSince(generating)),
          e);
    }
    Trace trace =
        new Trace(
            chunks.size(),
            top,
            prompt.sources().size(),
            result.model(),
            systemPrompt.version(),
            result.inputTokens(),
            result.outputTokens(),
            result.cachedInputTokens(),
            retrievalMillis,
            millisSince(generating));

    Optional<ModelAnswer> output = ModelAnswer.parse(json, result.text());
    if (output.isEmpty()) {
      throw new AnswerFailedException("The chat model's output is malformed", trace, null);
    }
    Optional<Validated> validated =
        CitationValidator.validate(output.get(), prompt.sources().size());
    if (validated.isEmpty()) {
      return abstention(trace);
    }
    List<Citation> citations =
        validated.get().ordinals().stream()
            .map(n -> citation(n, prompt.sources().get(n - 1)))
            .toList();
    return new Answer(AnswerOutcome.ANSWERED, validated.get().text(), citations, trace);
  }

  private Answer abstention(Trace trace) {
    return new Answer(
        AnswerOutcome.INSUFFICIENT_EVIDENCE, properties.abstentionMessage(), List.of(), trace);
  }

  private Citation citation(int ordinal, RetrievedChunk chunk) {
    return new Citation(
        ordinal,
        chunk.documentId(),
        chunk.title(),
        chunk.pageStart(),
        chunk.pageEnd(),
        chunk.chunkIndex(),
        snippet(chunk.text(), properties.snippetLength()),
        chunk.similarity());
  }

  /** The start of a chunk, cut at a space where one is near the limit. */
  static String snippet(String text, int maxLength) {
    if (text.length() <= maxLength) {
      return text;
    }
    int cut = text.lastIndexOf(' ', maxLength - 1);
    if (cut < maxLength / 2) {
      cut = maxLength - 1;
      if (Character.isHighSurrogate(text.charAt(cut - 1))) {
        cut--;
      }
    }
    return text.substring(0, cut).stripTrailing() + "…";
  }

  private static long millisSince(long nanos) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - nanos);
  }
}
