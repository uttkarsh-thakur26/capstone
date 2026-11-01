package in.edu.bennett.capstone.knowledge.conversation;

import in.edu.bennett.capstone.knowledge.assistant.Answer;
import in.edu.bennett.capstone.knowledge.assistant.Answer.Citation;
import in.edu.bennett.capstone.knowledge.assistant.Answer.Trace;
import in.edu.bennett.capstone.knowledge.assistant.AnswerFailedException;
import in.edu.bennett.capstone.knowledge.assistant.AnswerOutcome;
import in.edu.bennett.capstone.knowledge.assistant.AnswerService;
import in.edu.bennett.capstone.knowledge.audit.AuditAction;
import in.edu.bennett.capstone.knowledge.audit.AuditEvent;
import in.edu.bennett.capstone.knowledge.audit.AuditOutcome;
import in.edu.bennett.capstone.knowledge.audit.AuditService;
import in.edu.bennett.capstone.knowledge.common.ApiException;
import in.edu.bennett.capstone.knowledge.conversation.AnswerResponse.CitationResponse;
import in.edu.bennett.capstone.knowledge.security.CurrentUser;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Conversations (specification 12) and asking in one (11.1). Phase 5 answers each question on its
 * own; history and follow-ups join in Phase 6.
 */
@Service
public class ConversationService {

  private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

  /** What an answer that failed says, stored with the {@code ERROR} outcome (RAG-6). */
  static final String ERROR_TEXT = "The answer could not be produced.";

  private final ConversationRepository conversations;
  private final MessageRepository messages;
  private final MessageCitationRepository citations;
  private final AnswerService assistant;
  private final AuditService audit;
  private final TransactionTemplate transactions;
  private final TransactionTemplate reads;
  private final Clock clock;

  ConversationService(
      ConversationRepository conversations,
      MessageRepository messages,
      MessageCitationRepository citations,
      AnswerService assistant,
      AuditService audit,
      PlatformTransactionManager transactionManager,
      Clock clock) {
    this.conversations = conversations;
    this.messages = messages;
    this.citations = citations;
    this.assistant = assistant;
    this.audit = audit;
    this.transactions = new TransactionTemplate(transactionManager);
    this.reads = new TransactionTemplate(transactionManager);
    this.reads.setReadOnly(true);
    this.clock = clock;
  }

  /** {@code POST /conversations}: a new, untitled conversation of the user. */
  @Transactional
  public ConversationResponse create(CurrentUser user) {
    return ConversationResponse.of(conversations.saveAndFlush(new Conversation(user.id())));
  }

  /**
   * {@code POST /conversations/{id}/messages}. Deliberately not transactional, so no transaction or
   * connection is held while a model works (4.5):
   *
   * <ol>
   *   <li>the conversation must be the user's, in a short read transaction (CONV-1);
   *   <li>the assistant answers, with no transaction (it runs its own short reads);
   *   <li>the question, the answer and its citation snapshots are stored in one short transaction,
   *       with the {@code AI_QUERY} audit entry.
   * </ol>
   *
   * A failed answer is stored with the {@code ERROR} outcome and answers 503 (RAG-6).
   */
  public AnswerResponse ask(CurrentUser user, UUID conversationId, AskRequest request) {
    String question = request.question().strip();
    reads.executeWithoutResult(
        status ->
            conversations.findByIdAndUserId(conversationId, user.id()).orElseThrow(this::notFound));
    Instant askedAt = Instant.now(clock);
    Answer answer;
    try {
      answer = assistant.answer(user, question, request.documentIds());
    } catch (AnswerFailedException e) {
      UUID messageId =
          store(
              user,
              conversationId,
              question,
              askedAt,
              new Answer(AnswerOutcome.ERROR, ERROR_TEXT, List.of(), e.trace()));
      logAnswer(user, conversationId, messageId, AnswerOutcome.ERROR, e.trace(), e.getMessage());
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "MODEL_UNAVAILABLE",
          "The answer could not be produced right now. Try again later.");
    }
    UUID messageId = store(user, conversationId, question, askedAt, answer);
    logAnswer(user, conversationId, messageId, answer.outcome(), answer.trace(), null);
    return new AnswerResponse(
        messageId,
        answer.outcome(),
        answer.text(),
        answer.citations().stream()
            .map(
                c ->
                    new CitationResponse(
                        c.ordinal(),
                        c.documentId(),
                        c.documentTitle(),
                        c.pageStart(),
                        c.pageEnd(),
                        c.snippet()))
            .toList());
  }

  /**
   * Stores the question and the answer under a lock on the conversation, so nothing is stored for a
   * conversation that was removed while the model worked.
   *
   * @return the id of the stored answer
   */
  private UUID store(
      CurrentUser user, UUID conversationId, String question, Instant askedAt, Answer answer) {
    return transactions.execute(
        status -> {
          Conversation conversation =
              conversations
                  .lockByIdAndUserId(conversationId, user.id())
                  .orElseThrow(this::notFound);
          Instant answeredAt = Instant.now(clock);
          if (!answeredAt.isAfter(askedAt)) {
            answeredAt = askedAt.plus(1, ChronoUnit.MICROS);
          }
          conversation.asked(question, answeredAt);
          messages.save(Message.question(conversationId, question, askedAt));
          Message stored =
              messages.save(
                  Message.answer(
                      conversationId,
                      answer.text(),
                      answer.outcome(),
                      answer.trace().model(),
                      answer.trace().promptVersion(),
                      answeredAt));
          for (Citation citation : answer.citations()) {
            citations.save(new MessageCitation(stored.getId(), citation));
          }
          // No question or answer text in the audit log (AUD-2, D14).
          audit.record(
              AuditEvent.of(
                      AuditAction.AI_QUERY,
                      answer.outcome() == AnswerOutcome.ERROR
                          ? AuditOutcome.FAILURE
                          : AuditOutcome.SUCCESS,
                      user.id(),
                      user.email())
                  .withResource("CONVERSATION", conversationId)
                  .withDetails(
                      Map.of(
                          "messageId", stored.getId().toString(),
                          "outcome", answer.outcome().name(),
                          "citations", answer.citations().size())));
          return stored.getId();
        });
  }

  /**
   * One line per question with what it cost, so the real price of answers can be read from the log.
   * Ids and numbers only: no question, prompt or answer text (SEC-10).
   */
  private static void logAnswer(
      CurrentUser user,
      UUID conversationId,
      UUID messageId,
      AnswerOutcome outcome,
      Trace trace,
      String failure) {
    Object[] fields = {
      user.id(),
      conversationId,
      messageId,
      outcome,
      trace.model(),
      trace.promptVersion(),
      trace.retrieved(),
      trace.topSimilarity() == null ? null : String.format("%.3f", trace.topSimilarity()),
      trace.sourcesSent(),
      trace.inputTokens(),
      trace.outputTokens(),
      trace.cachedInputTokens(),
      trace.retrievalMillis(),
      trace.generationMillis()
    };
    String line =
        "Question user={} conversation={} message={} outcome={} model={} prompt={} retrieved={}"
            + " topSimilarity={} sources={} inputTokens={} outputTokens={} cachedInputTokens={}"
            + " retrievalMs={} generationMs={}";
    if (failure == null) {
      log.info(line, fields);
    } else {
      log.warn(line + " failure=\"" + failure.replace("{", "(").replace("}", ")") + "\"", fields);
    }
  }

  private ApiException notFound() {
    return new ApiException(
        HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested resource does not exist.");
  }
}
