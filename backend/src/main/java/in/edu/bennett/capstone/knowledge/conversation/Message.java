package in.edu.bennett.capstone.knowledge.conversation;

import in.edu.bennett.capstone.knowledge.assistant.AnswerOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

/**
 * A question or an answer in a conversation (table {@code message}, specification 7.2). Messages
 * are never changed. A question and its answer are stored together once the answer is known, with
 * times that place the question directly before its answer ({@code ConversationService}).
 */
@Entity
@Table(name = "message")
@Immutable
class Message {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(nullable = false)
  private UUID conversationId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private MessageRole role;

  @Column(nullable = false)
  private String content;

  @Enumerated(EnumType.STRING)
  private AnswerOutcome outcome;

  private String model;

  private String promptVersion;

  @Column(nullable = false)
  private Instant createdAt;

  protected Message() {}

  private Message(
      UUID conversationId,
      MessageRole role,
      String content,
      AnswerOutcome outcome,
      String model,
      String promptVersion,
      Instant createdAt) {
    this.conversationId = conversationId;
    this.role = role;
    this.content = content;
    this.outcome = outcome;
    this.model = model;
    this.promptVersion = promptVersion;
    this.createdAt = createdAt;
  }

  static Message question(UUID conversationId, String text, Instant askedAt) {
    return new Message(conversationId, MessageRole.USER, text, null, null, null, askedAt);
  }

  static Message answer(
      UUID conversationId,
      String text,
      AnswerOutcome outcome,
      String model,
      String promptVersion,
      Instant answeredAt) {
    return new Message(
        conversationId, MessageRole.ASSISTANT, text, outcome, model, promptVersion, answeredAt);
  }

  UUID getId() {
    return id;
  }

  MessageRole getRole() {
    return role;
  }

  String getContent() {
    return content;
  }

  AnswerOutcome getOutcome() {
    return outcome;
  }

  Instant getCreatedAt() {
    return createdAt;
  }
}
