package in.edu.bennett.capstone.knowledge.conversation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * A user's conversation (table {@code conversation}, specification 7.2). Private to its user
 * (CONV-1). Never leaves the {@code conversation} module (4.3).
 */
@Entity
@Table(name = "conversation")
@EntityListeners(AuditingEntityListener.class)
class Conversation {

  /** CONV-3: a title is the first question, cut to this length. */
  static final int TITLE_LENGTH = 80;

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(nullable = false, updatable = false)
  private UUID userId;

  private String title;

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(nullable = false)
  private Instant updatedAt;

  protected Conversation() {}

  Conversation(UUID userId) {
    this.userId = userId;
  }

  UUID getId() {
    return id;
  }

  UUID getUserId() {
    return userId;
  }

  String getTitle() {
    return title;
  }

  Instant getCreatedAt() {
    return createdAt;
  }

  Instant getUpdatedAt() {
    return updatedAt;
  }

  /**
   * Records a new question: the first one names the conversation (CONV-3, no model call), and every
   * one moves it to the top of the user's list.
   */
  void asked(String question, Instant at) {
    if (title == null) {
      title = titleOf(question);
    }
    updatedAt = at;
  }

  /** CONV-2: the owner names the conversation. */
  void rename(String newTitle) {
    title = newTitle;
  }

  static String titleOf(String question) {
    String line = question.strip().replaceAll("\\s+", " ");
    if (line.codePointCount(0, line.length()) <= TITLE_LENGTH) {
      return line;
    }
    int end = line.offsetByCodePoints(0, TITLE_LENGTH - 1);
    return line.substring(0, end).stripTrailing() + "…";
  }
}
