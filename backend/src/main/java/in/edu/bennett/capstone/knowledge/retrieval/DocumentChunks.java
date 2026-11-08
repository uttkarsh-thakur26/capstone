package in.edu.bennett.capstone.knowledge.retrieval;

import in.edu.bennett.capstone.knowledge.security.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The chunks of whole documents that a user names, for summaries and comparisons (Section 13). Each
 * method is one statement with the readable predicate and the indexed status in its {@code WHERE}
 * clause (ACL-E2, ACL-4), run in its own short read transaction, so the caller holds none while it
 * calls a model (4.5). The caller has already answered 404 for an unreadable document; these
 * statements check access again, so a grant removed in between still takes effect (ACL-7).
 */
@Service
public class DocumentChunks {

  private final ChunkSearch chunks;

  DocumentChunks(ChunkSearch chunks) {
    this.chunks = chunks;
  }

  /** Every chunk, in the order of the text; empty unless readable and indexed. */
  public List<DocumentChunk> of(CurrentUser user, UUID documentId) {
    return chunks.documentChunks(user, documentId);
  }

  /** Every pair of a left and a right chunk with its similarity; empty unless both qualify. */
  public List<ChunkSimilarity> similarities(CurrentUser user, UUID leftId, UUID rightId) {
    return chunks.similarities(user, leftId, rightId);
  }
}
