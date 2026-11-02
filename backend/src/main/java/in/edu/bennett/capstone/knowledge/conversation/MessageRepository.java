package in.edu.bennett.capstone.knowledge.conversation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface MessageRepository extends JpaRepository<Message, UUID> {

  /** The time of the conversation's last message, or null when it has none. */
  @Query("select max(m.createdAt) from Message m where m.conversationId = :conversationId")
  Instant latestIn(UUID conversationId);

  /** The conversation's messages in the order they were stored: each question before its answer. */
  @Query(
      "select m from Message m where m.conversationId = :conversationId"
          + " order by m.createdAt, m.id")
  List<Message> inOrder(UUID conversationId);

  /** The conversation's last messages, newest first. */
  @Query(
      "select m from Message m where m.conversationId = :conversationId"
          + " order by m.createdAt desc, m.id desc")
  List<Message> latest(UUID conversationId, Pageable limit);
}
