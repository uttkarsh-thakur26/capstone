package in.edu.bennett.capstone.knowledge.assistant;

import in.edu.bennett.capstone.knowledge.ai.ChatFailedException;
import in.edu.bennett.capstone.knowledge.ai.ChatGenerator;
import in.edu.bennett.capstone.knowledge.ai.ChatResult;
import in.edu.bennett.capstone.knowledge.ai.EmbeddingFailedException;
import in.edu.bennett.capstone.knowledge.assistant.Answer.Citation;
import in.edu.bennett.capstone.knowledge.assistant.Answer.Trace;
import in.edu.bennett.capstone.knowledge.assistant.Answer.Turn;
import in.edu.bennett.capstone.knowledge.assistant.Answer.Usage;
import in.edu.bennett.capstone.knowledge.assistant.CitationValidator.Validated;
import in.edu.bennett.capstone.knowledge.assistant.FollowUpRewriter.Rewrite;
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
 * Grounded question answering, steps 3 to 8 of specification 11.1. Deliberately not transactional:
 * retrieval runs its own short read transactions, and the model calls run with no transaction or
 * connection held (4.5).
 *
 * <ol>
 *   <li>A follow-up, a question with earlier turns, becomes one standalone search query ({@link
 *       FollowUpRewriter}, one extra model call).
 *   <li>Retrieve the user's retrievable chunks for that query (RET-1, through {@link
 *       SearchService#retrieve}, which embeds it with the query prefix).
 *   <li>Evidence gate: no chunk, or none at least as similar to the query as the floor, is
 *       insufficient evidence, and the model is not asked to answer.
 *   <li>Assemble the prompt from the retrieved chunks, the history and the user's own question
 *       (RAG-1).
 *   <li>Generate structured output.
 *   <li>Keep only citations of sources that were sent, and fill their titles, pages and snippets
 *       from the retrieved chunks, never from the model (RAG-3).
 * </ol>
 */
@Service
public class AnswerService {

  private final SearchService search;
  private final ChatGenerator generator;
  private final FollowUpRewriter rewriter;
  private final SystemPrompt systemPrompt;
  private final PromptAssembler assembler;
  private final AssistantProperties properties;
  private final RetrievalProperties retrieval;
  private final JsonMapper json;

  AnswerService(
      SearchService search,
      ChatGenerator generator,
      FollowUpRewriter rewriter,
      SystemPrompt systemPrompt,
      PromptAssembler assembler,
      AssistantProperties properties,
      RetrievalProperties retrieval,
      JsonMapper json) {
    this.search = search;
    this.generator = generator;
    this.rewriter = rewriter;
    this.systemPrompt = systemPrompt;
    this.assembler = assembler;
    this.properties = properties;
    this.retrieval = retrieval;
    this.json = json;
  }

  /**
   * @param history earlier turns of the conversation, oldest first, already filtered by CONV-5;
   *     empty for a first question
   * @param documentIds restrict retrieval to these documents (CONV-7); null or empty for all
   * @throws AnswerFailedException when a model fails or the output is malformed
   */
  public Answer answer(
      CurrentUser user, String question, List<Turn> history, List<UUID> documentIds) {
    List<Turn> turns = assembler.fit(history);
    Rewrite rewrite;
    try {
      rewrite = rewriter.rewrite(turns, question);
    } catch (ChatFailedException e) {
      throw new AnswerFailedException(
          "The follow-up could not be rewritten", Trace.start(turns.size(), null), e);
    }
    Trace trace =
        Trace.start(turns.size(), rewrite.result() == null ? null : Usage.of(rewrite.result()));

    long started = System.nanoTime();
    List<RetrievedChunk> chunks;
    try {
      chunks = search.retrieve(user, rewrite.query(), documentIds);
    } catch (EmbeddingFailedException e) {
      throw new AnswerFailedException(
          "The question could not be embedded", trace.retrieved(0, null, millisSince(started)), e);
    }
    // The gate compares a cosine similarity with the floor in every mode: a hybrid ranking's fused
    // score is not a similarity, so the best similarity among the chunks retrieved is used, and
    // those are the chunks the prompt is built from (ADR-040).
    Double top =
        chunks.stream()
            .mapToDouble(RetrievedChunk::similarity)
            .boxed()
            .max(Double::compare)
            .orElse(null);
    trace = trace.retrieved(chunks.size(), top, millisSince(started));
    if (top == null || top < retrieval.similarityFloor()) {
      return abstention(trace);
    }
    AssembledPrompt prompt = assembler.assemble(chunks, turns, question);
    if (prompt.sources().isEmpty()) {
      return abstention(trace);
    }

    long generating = System.nanoTime();
    ChatResult result;
    try {
      result = generator.generate(systemPrompt.text(), prompt.userMessage(), ModelAnswer.SCHEMA);
    } catch (ChatFailedException e) {
      throw new AnswerFailedException(
          "The chat model gave no answer",
          trace.generated(
              prompt.sources().size(), null, systemPrompt.version(), null, millisSince(generating)),
          e);
    }
    trace =
        trace.generated(
            prompt.sources().size(),
            result.model(),
            systemPrompt.version(),
            Usage.of(result),
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
