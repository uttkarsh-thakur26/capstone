package in.edu.bennett.capstone.knowledge.conversation;

import in.edu.bennett.capstone.knowledge.assistant.Answer;
import in.edu.bennett.capstone.knowledge.assistant.Answer.Citation;
import in.edu.bennett.capstone.knowledge.assistant.Answer.Trace;
import in.edu.bennett.capstone.knowledge.assistant.Answer.Turn;
import in.edu.bennett.capstone.knowledge.assistant.Answer.Usage;
import in.edu.bennett.capstone.knowledge.assistant.AnswerFailedException;
import in.edu.bennett.capstone.knowledge.assistant.AnswerOutcome;
import in.edu.bennett.capstone.knowledge.assistant.AnswerService;
import in.edu.bennett.capstone.knowledge.assistant.AssistantProperties;
import in.edu.bennett.capstone.knowledge.audit.AuditAction;
import in.edu.bennett.capstone.knowledge.audit.AuditEvent;
import in.edu.bennett.capstone.knowledge.audit.AuditOutcome;
import in.edu.bennett.capstone.knowledge.audit.AuditService;
import in.edu.bennett.capstone.knowledge.common.ApiException;
import in.edu.bennett.capstone.knowledge.common.PageResponse;
import in.edu.bennett.capstone.knowledge.document.DocumentService;
import in.edu.bennett.capstone.knowledge.security.CurrentUser;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Conversations (specification 12) and asking in one (11.1). A conversation is private to its user:
 * every method looks it up by id and user together, so another user's id behaves as a missing one
 * (CONV-1), admins included (D15).
 */
@Service
public class ConversationService {

  private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

  /** What an answer that failed says, stored with the {@code ERROR} outcome (RAG-6). */
  static final String ERROR_TEXT = "The answer could not be produced.";

  /**
   * How many of the latest messages are read to find the history: more than the turns used, so that
   * turns the CONV-5 filter leaves out can be passed over.
   */
  private static final int HISTORY_MESSAGES = 40;

  private final ConversationRepository conversations;
  private final MessageRepository messages;
  private final MessageCitationRepository citations;
  private final AnswerService assistant;
  private final AssistantProperties assistantProperties;
  private final DocumentService documents;
  private final AuditService audit;
  private final TransactionTemplate transactions;
  private final TransactionTemplate reads;
  private final Clock clock;

  ConversationService(
      ConversationRepository conversations,
      MessageRepository messages,
      MessageCitationRepository citations,
      AnswerService assistant,
      AssistantProperties assistantProperties,
      DocumentService documents,
      AuditService audit,
      PlatformTransactionManager transactionManager,
      Clock clock) {
    this.conversations = conversations;
    this.messages = messages;
    this.citations = citations;
    this.assistant = assistant;
    this.assistantProperties = assistantProperties;
    this.documents = documents;
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

  /** CONV-2: the user's own conversations, the most recently used first. */
  @Transactional(readOnly = true)
  public PageResponse<ConversationResponse> list(CurrentUser user, int page, int size) {
    return PageResponse.of(
        conversations
            .findByUserId(
                user.id(),
                PageRequest.of(
                    page,
                    size,
                    Sort.by(Sort.Direction.DESC, "updatedAt")
                        .and(Sort.by(Sort.Direction.DESC, "id"))))
            .map(ConversationResponse::of));
  }

  /**
   * CONV-2, CONV-6: the conversation with its messages and citations. A citation whose document was
   * deleted, or which the user can no longer read, is unavailable and names nothing; the answer
   * text stays.
   */
  @Transactional(readOnly = true)
  public ConversationDetailResponse get(CurrentUser user, UUID id) {
    Conversation conversation = owned(user, id);
    List<Message> all = messages.inOrder(id);
    Map<UUID, List<MessageCitation>> cited = citationsOf(all);
    Set<UUID> readable = documents.readableIds(user, documentIds(cited));
    List<MessageResponse> items =
        all.stream()
            .map(
                message ->
                    new MessageResponse(
                        message.getId(),
                        message.getRole(),
                        message.getContent(),
                        message.getOutcome(),
                        message.getCreatedAt(),
                        cited.getOrDefault(message.getId(), List.of()).stream()
                            .map(citation -> citation(citation, readable))
                            .toList()))
            .toList();
    return new ConversationDetailResponse(
        conversation.getId(),
        conversation.getTitle(),
        conversation.getCreatedAt(),
        conversation.getUpdatedAt(),
        items);
  }

  /** CONV-2. */
  @Transactional
  public ConversationResponse rename(CurrentUser user, UUID id, String title) {
    Conversation conversation = owned(user, id);
    conversation.rename(title.strip());
    return ConversationResponse.of(conversations.saveAndFlush(conversation));
  }

  /** CONV-2: a hard delete; the database removes its messages and citations with it. */
  @Transactional
  public void delete(CurrentUser user, UUID id) {
    conversations.delete(owned(user, id));
  }

  /**
   * {@code POST /conversations/{id}/messages}. Deliberately not transactional, so no transaction or
   * connection is held while a model works (4.5):
   *
   * <ol>
   *   <li>the conversation must be the user's, and its history is read and filtered, in one short
   *       read transaction (CONV-1, CONV-4, CONV-5);
   *   <li>the assistant answers, with no transaction (it runs its own short reads);
   *   <li>the question, the answer and its citation snapshots are stored in one short transaction,
   *       with the {@code AI_QUERY} audit entry.
   * </ol>
   *
   * A failed answer is stored with the {@code ERROR} outcome and answers 503 (RAG-6). The failure
   * is logged even when it cannot be stored, for example because the conversation was deleted.
   */
  public AnswerResponse ask(CurrentUser user, UUID conversationId, AskRequest request) {
    String question = request.question().strip();
    List<Turn> history = reads.execute(status -> history(user, owned(user, conversationId)));
    Answer answer;
    try {
      answer = assistant.answer(user, question, history, request.documentIds());
    } catch (AnswerFailedException e) {
      UUID messageId = null;
      try {
        messageId =
            store(
                user,
                conversationId,
                question,
                new Answer(AnswerOutcome.ERROR, ERROR_TEXT, List.of(), e.trace()));
      } finally {
        logAnswer(user, conversationId, messageId, AnswerOutcome.ERROR, e.trace(), e.getMessage());
      }
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "MODEL_UNAVAILABLE",
          "The answer could not be produced right now. Try again later.");
    }
    UUID messageId = store(user, conversationId, question, answer);
    logAnswer(user, conversationId, messageId, answer.outcome(), answer.trace(), null);
    return new AnswerResponse(
        messageId,
        answer.outcome(),
        answer.text(),
        answer.citations().stream()
            .map(
                c ->
                    CitationResponse.available(
                        c.ordinal(),
                        c.documentId(),
                        c.documentTitle(),
                        c.pageStart(),
                        c.pageEnd(),
                        c.snippet()))
            .toList());
  }

  /**
   * CONV-4, CONV-5, ADR-038: the last turns that may go to the model, oldest first. A turn is left
   * out, question and answer together, when its answer failed or cites a document the user can no
   * longer retrieve: deleted, no longer readable, archived or not indexed. What reaches the model
   * is then still only what the user may retrieve now (RAG-1).
   */
  private List<Turn> history(CurrentUser user, Conversation conversation) {
    int wanted = assistantProperties.historyTurns();
    if (wanted == 0) {
      return List.of();
    }
    List<Message> latest =
        new ArrayList<>(messages.latest(conversation.getId(), PageRequest.of(0, HISTORY_MESSAGES)));
    latest = latest.reversed();
    Map<UUID, List<MessageCitation>> cited = citationsOf(latest);
    Set<UUID> retrievable = documents.retrievableIds(user, documentIds(cited));
    List<Turn> turns = new ArrayList<>();
    for (int i = 0; i + 1 < latest.size(); i++) {
      Message question = latest.get(i);
      Message answer = latest.get(i + 1);
      if (question.getRole() != MessageRole.USER || answer.getRole() != MessageRole.ASSISTANT) {
        continue;
      }
      i++;
      boolean usable =
          answer.getOutcome() != AnswerOutcome.ERROR
              && cited.getOrDefault(answer.getId(), List.of()).stream()
                  .allMatch(
                      citation ->
                          citation.getDocumentId() != null
                              && retrievable.contains(citation.getDocumentId()));
      if (usable) {
        turns.add(new Turn(question.getContent(), answer.getContent()));
      }
    }
    return List.copyOf(turns.subList(Math.max(0, turns.size() - wanted), turns.size()));
  }

  private Conversation owned(CurrentUser user, UUID id) {
    return conversations.findByIdAndUserId(id, user.id()).orElseThrow(this::notFound);
  }

  private Map<UUID, List<MessageCitation>> citationsOf(List<Message> list) {
    List<UUID> answers =
        list.stream()
            .filter(message -> message.getRole() == MessageRole.ASSISTANT)
            .map(Message::getId)
            .toList();
    if (answers.isEmpty()) {
      return Map.of();
    }
    return citations.findByMessageIdInOrderByOrdinal(answers).stream()
        .collect(Collectors.groupingBy(MessageCitation::getMessageId));
  }

  private static Set<UUID> documentIds(Map<UUID, List<MessageCitation>> cited) {
    return cited.values().stream()
        .flatMap(List::stream)
        .map(MessageCitation::getDocumentId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  private static CitationResponse citation(MessageCitation citation, Set<UUID> readable) {
    if (citation.getDocumentId() == null || !readable.contains(citation.getDocumentId())) {
      return CitationResponse.unavailable(citation.getOrdinal());
    }
    return CitationResponse.available(
        citation.getOrdinal(),
        citation.getDocumentId(),
        citation.getDocumentTitle(),
        citation.getPageStart(),
        citation.getPageEnd(),
        citation.getSnippet());
  }

  /**
   * Stores the question and the answer under a lock on the conversation, so nothing is stored for a
   * conversation that was removed while the model worked.
   *
   * <p>Both times are taken under the lock and follow the conversation's last message, the answer
   * one microsecond after its question (the database's precision). Two questions asked at once are
   * therefore stored one exchange after the other, and each question sorts directly before its own
   * answer, whichever model call finished first.
   *
   * @return the id of the stored answer
   */
  private UUID store(CurrentUser user, UUID conversationId, String question, Answer answer) {
    return transactions.execute(
        status -> {
          Conversation conversation =
              conversations
                  .lockByIdAndUserId(conversationId, user.id())
                  .orElseThrow(this::notFound);
          Instant askedAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
          Instant latest = messages.latestIn(conversationId);
          if (latest != null && !askedAt.isAfter(latest)) {
            askedAt = latest.plus(1, ChronoUnit.MICROS);
          }
          Instant answeredAt = askedAt.plus(1, ChronoUnit.MICROS);
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
   * One line per question with what it cost, so the real price of answers can be read from the log:
   * the follow-up rewrite and the answer each have their own token counts. Ids and numbers only: no
   * question, query, prompt or answer text (SEC-10).
   */
  private static void logAnswer(
      CurrentUser user,
      UUID conversationId,
      UUID messageId,
      AnswerOutcome outcome,
      Trace trace,
      String failure) {
    Usage rewrite = trace.rewrite();
    Usage answer = trace.answer();
    Object[] fields = {
      user.id(),
      conversationId,
      messageId,
      outcome,
      trace.model(),
      trace.promptVersion(),
      trace.historyTurns(),
      rewrite == null ? null : rewrite.inputTokens(),
      rewrite == null ? null : rewrite.outputTokens(),
      trace.retrieved(),
      trace.topSimilarity() == null ? null : String.format("%.3f", trace.topSimilarity()),
      trace.sourcesSent(),
      answer == null ? null : answer.inputTokens(),
      answer == null ? null : answer.outputTokens(),
      answer == null ? null : answer.cachedInputTokens(),
      trace.retrievalMillis(),
      trace.generationMillis()
    };
    String line =
        "Question user={} conversation={} message={} outcome={} model={} prompt={} historyTurns={}"
            + " rewriteInputTokens={} rewriteOutputTokens={} retrieved={} topSimilarity={}"
            + " sources={} inputTokens={} outputTokens={} cachedInputTokens={} retrievalMs={}"
            + " generationMs={}";
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
