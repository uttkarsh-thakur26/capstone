package in.edu.bennett.capstone.knowledge.conversation;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface MessageRepository extends JpaRepository<Message, UUID> {

  /** The time of the conversation's last message, or null when it has none. */
  @Query("select max(m.createdAt) from Message m where m.conversationId = :conversationId")
  Instant latestIn(UUID conversationId);
}
