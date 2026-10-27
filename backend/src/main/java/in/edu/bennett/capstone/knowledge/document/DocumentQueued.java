package in.edu.bennett.capstone.knowledge.document;

import java.util.UUID;

/**
 * A document became {@code UPLOADED}, by upload or reprocess. Published inside the transaction that
 * queued it; ingestion listens after commit to start work at once instead of at its next poll
 * (ING-1).
 */
public record DocumentQueued(UUID documentId) {}
