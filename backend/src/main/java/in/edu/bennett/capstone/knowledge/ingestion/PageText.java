package in.edu.bennett.capstone.knowledge.ingestion;

/**
 * The text of one page.
 *
 * @param page the page number in the PDF, from 1. Pages without text are left out, so the page
 *     number is never the position in a list
 */
record PageText(int page, String text) {}
