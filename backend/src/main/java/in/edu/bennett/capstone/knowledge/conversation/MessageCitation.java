package in.edu.bennett.capstone.knowledge.conversation;

import in.edu.bennett.capstone.knowledge.assistant.Answer.Citation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * A snapshot of what an answer cited (table {@code message_citation}, DATA-1): title, pages, chunk
 * index and snippet as they were, taken from the stored chunk (RAG-3). The document link becomes
 * null when the document is deleted (DATA-2).
 */
@Entity
@Table(name = "message_citation")
@EntityListeners(AuditingEntityListener.class)
@Immutable
class MessageCitation {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(nullable = false)
  private UUID messageId;

  private int ordinal;

  private UUID documentId;

  @Column(nullable = false)
  private String documentTitle;

  private int pageStart;

  private int pageEnd;

  private int chunkIndex;

  @Column(nullable = false)
  private String snippet;

  private double score;

  @CreatedDate
  @Column(nullable = false)
  private Instant createdAt;

  protected MessageCitation() {}

  MessageCitation(UUID messageId, Citation citation) {
    this.messageId = messageId;
    this.ordinal = citation.ordinal();
    this.documentId = citation.documentId();
    this.documentTitle = citation.documentTitle();
    this.pageStart = citation.pageStart();
    this.pageEnd = citation.pageEnd();
    this.chunkIndex = citation.chunkIndex();
    this.snippet = citation.snippet();
    this.score = citation.score();
  }
}
