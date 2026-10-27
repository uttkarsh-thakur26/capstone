package in.edu.bennett.capstone.knowledge.document;

import java.time.Instant;
import java.util.UUID;

/**
 * One claimed processing run of a document (ING-1, ADR-034). The run id is the lease: every write
 * of the run names it, so a run that was recovered or replaced can no longer change the document.
 *
 * @param storageKey where the document's file is stored
 * @param pageCount the page count recorded at upload
 */
public record ProcessingRun(
    UUID documentId, UUID runId, String storageKey, int pageCount, Instant startedAt) {}
