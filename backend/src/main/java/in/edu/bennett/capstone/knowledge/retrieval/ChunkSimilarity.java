package in.edu.bennett.capstone.knowledge.retrieval;

/**
 * How close in meaning one chunk of the left document is to one chunk of the right document
 * (CMP-2).
 *
 * @param similarity {@code 1 - cosine distance} of the two chunks' vectors
 */
public record ChunkSimilarity(int leftIndex, int rightIndex, double similarity) {}
