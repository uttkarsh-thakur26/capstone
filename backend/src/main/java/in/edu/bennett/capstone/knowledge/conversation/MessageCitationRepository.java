package in.edu.bennett.capstone.knowledge.conversation;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface MessageCitationRepository extends JpaRepository<MessageCitation, UUID> {

  List<MessageCitation> findByMessageIdInOrderByOrdinal(Collection<UUID> messageIds);
}
