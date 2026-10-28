package in.edu.bennett.capstone.knowledge.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import in.edu.bennett.capstone.knowledge.retrieval.SearchResponse.Highlight;
import org.junit.jupiter.api.Test;

class HighlightsTest {

  private static final char START = Highlights.START;
  private static final char STOP = Highlights.STOP;

  @Test
  void markersBecomePositionsInTheTextWithoutThem() {
    Highlights.Marked marked =
        Highlights.parse("Submit the " + START + "form" + STOP + " by " + START + "Friday" + STOP);

    assertThat(marked.text()).isEqualTo("Submit the form by Friday");
    assertThat(marked.highlights()).containsExactly(new Highlight(11, 15), new Highlight(19, 25));
  }

  @Test
  void textWithoutMarkersIsUnchanged() {
    Highlights.Marked marked = Highlights.parse("No match here");

    assertThat(marked.text()).isEqualTo("No match here");
    assertThat(marked.highlights()).isEmpty();
  }

  @Test
  void emptyAndUnbalancedMarkersGiveNoHighlight() {
    Highlights.Marked marked =
        Highlights.parse("a" + START + STOP + "b" + STOP + "c" + START + "d");

    assertThat(marked.text()).isEqualTo("abcd");
    assertThat(marked.highlights()).isEmpty();
  }

  @Test
  void positionsCountUtf16UnitsLikeJavaScript() {
    Highlights.Marked marked = Highlights.parse("📘 " + START + "Rule" + STOP);

    assertThat(marked.highlights()).containsExactly(new Highlight(3, 7));
  }
}
