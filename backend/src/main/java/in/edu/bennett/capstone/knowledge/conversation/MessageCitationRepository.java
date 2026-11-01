package in.edu.bennett.capstone.knowledge.conversation;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface MessageCitationRepository extends JpaRepository<MessageCitation, UUID> {}
