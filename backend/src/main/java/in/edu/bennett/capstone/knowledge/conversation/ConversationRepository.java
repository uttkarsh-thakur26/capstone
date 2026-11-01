package in.edu.bennett.capstone.knowledge.conversation;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface ConversationRepository extends JpaRepository<Conversation, UUID> {

  /** CONV-1: the conversation only if it is this user's, so any other id behaves as missing. */
  Optional<Conversation> findByIdAndUserId(UUID id, UUID userId);

  /**
   * The same, locked until the transaction ends: an answer is stored only while its conversation
   * still exists, and two answers in one conversation are stored one after the other.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from Conversation c where c.id = :id and c.userId = :userId")
  Optional<Conversation> lockByIdAndUserId(UUID id, UUID userId);
}
