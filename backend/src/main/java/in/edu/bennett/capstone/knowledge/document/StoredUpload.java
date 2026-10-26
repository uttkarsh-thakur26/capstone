package in.edu.bennett.capstone.knowledge.document;

/**
 * An uploaded file that passed the checks and is in storage, before its row exists.
 *
 * @param filename the user's filename, sanitized; metadata only, never part of a path (DOC-2)
 */
record StoredUpload(
    String filename,
    String contentType,
    long sizeBytes,
    String sha256,
    String storageKey,
    int pageCount) {}
