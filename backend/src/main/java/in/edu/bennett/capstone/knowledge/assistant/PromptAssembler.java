package in.edu.bennett.capstone.knowledge.assistant;

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

  PromptAssembler(TokenCountEstimator tokens, int budget) {
    this.tokens = tokens;
    this.budget = budget;
  }

  AssembledPrompt assemble(List<RetrievedChunk> chunks, String question) {
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
        "<sources>\n" + blocks + "</sources>\n\n<question>\n" + escape(question) + "\n</question>";
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
