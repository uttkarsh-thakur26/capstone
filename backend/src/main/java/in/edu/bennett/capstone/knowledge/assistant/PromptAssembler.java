package in.edu.bennett.capstone.knowledge.assistant;

import in.edu.bennett.capstone.knowledge.assistant.Answer.Turn;
import in.edu.bennett.capstone.knowledge.retrieval.RetrievedChunk;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.tokenizer.TokenCountEstimator;

/**
 * The user message of a question (11.1 step 6): the numbered sources in the delimited format of
 * 11.2, then the question. Nothing else goes in (RAG-1).
 *
 * <p>Titles, chunk text and the question are escaped, so no text from a document can close a {@code
 * source} element or the {@code sources} block early and pass itself off as instructions. Sources
 * are added best first while they fit the token budget (RAG-5); the first one that does not fit
 * ends the list, so the numbering always follows the ranking.
 */
class PromptAssembler {

  /** The user message and the chunks it numbers: source {@code n} is {@code sources[n - 1]}. */
  record AssembledPrompt(String userMessage, List<RetrievedChunk> sources) {}

  private final TokenCountEstimator tokens;
  private final int budget;
  private final int historyBudget;

  PromptAssembler(TokenCountEstimator tokens, int budget, int historyBudget) {
    this.tokens = tokens;
    this.budget = budget;
    this.historyBudget = historyBudget;
  }

  /**
   * The most recent turns whose blocks fit the history budget (RAG-5); older turns go first, so the
   * turn just before the question is the last to be dropped.
   */
  List<Turn> fit(List<Turn> history) {
    List<Turn> kept = new ArrayList<>();
    int used = 0;
    for (int i = history.size() - 1; i >= 0; i--) {
      int cost = tokens.estimate(turn(history.get(i)));
      if (used + cost > historyBudget) {
        break;
      }
      used += cost;
      kept.addFirst(history.get(i));
    }
    return List.copyOf(kept);
  }

  /**
   * The user message of the follow-up rewrite (11.1 step 3): the history, then the question, in the
   * same escaped format as an answer's prompt.
   */
  String followUp(List<Turn> history, String question) {
    return historyBlock(history) + questionBlock(question);
  }

  /**
   * @param history earlier turns, already fitted to the budget; they help the model understand the
   *     question and are never numbered as sources
   */
  AssembledPrompt assemble(List<RetrievedChunk> chunks, List<Turn> history, String question) {
    List<RetrievedChunk> sources = new ArrayList<>();
    StringBuilder blocks = new StringBuilder();
    int used = 0;
    for (RetrievedChunk chunk : chunks) {
      String block = source(sources.size() + 1, chunk);
      int cost = tokens.estimate(block);
      if (used + cost > budget) {
        break;
      }
      used += cost;
      sources.add(chunk);
      blocks.append(block);
    }
    String message =
        "<sources>\n" + blocks + "</sources>\n\n" + historyBlock(history) + questionBlock(question);
    return new AssembledPrompt(message, List.copyOf(sources));
  }

  private static String source(int n, RetrievedChunk chunk) {
    StringBuilder block =
        new StringBuilder("<source n=\"")
            .append(n)
            .append("\" title=\"")
            .append(escape(chunk.title()))
            .append('"');
    if (chunk.documentDate() != null) {
      block.append(" date=\"").append(chunk.documentDate()).append('"');
    }
    block.append(" pages=\"").append(chunk.pageStart());
    if (chunk.pageEnd() != chunk.pageStart()) {
      block.append('-').append(chunk.pageEnd());
    }
    return block.append("\">\n").append(escape(chunk.text())).append("\n</source>\n").toString();
  }

  private static String historyBlock(List<Turn> history) {
    if (history.isEmpty()) {
      return "";
    }
    StringBuilder block = new StringBuilder("<history>\n");
    history.forEach(turn -> block.append(turn(turn)));
    return block.append("</history>\n\n").toString();
  }

  /**
   * One earlier turn. The answer's {@code [n]} markers are removed: they numbered that turn's
   * sources, not this prompt's, and the model must cite only this prompt's sources.
   */
  private static String turn(Turn turn) {
    return "<turn>\n<question>\n"
        + escape(turn.question())
        + "\n</question>\n<answer>\n"
        + escape(CitationValidator.withoutMarkers(turn.answer()))
        + "\n</answer>\n</turn>\n";
  }

  private static String questionBlock(String question) {
    return "<question>\n" + escape(question) + "\n</question>";
  }

  /** XML escaping, for element text and attribute values alike. */
  static String escape(String text) {
    StringBuilder escaped = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
        case '&' -> escaped.append("&amp;");
        case '<' -> escaped.append("&lt;");
        case '>' -> escaped.append("&gt;");
        case '"' -> escaped.append("&quot;");
        default -> escaped.append(c);
      }
    }
    return escaped.toString();
  }
}
