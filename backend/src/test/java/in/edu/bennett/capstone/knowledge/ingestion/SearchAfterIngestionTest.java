package in.edu.bennett.capstone.knowledge.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import in.edu.bennett.capstone.knowledge.TestPdfs;
import in.edu.bennett.capstone.knowledge.ai.EmbeddingProperties;
import in.edu.bennett.capstone.knowledge.ingestion.IngestionPipeline.Outcome;
import in.edu.bennett.capstone.knowledge.retrieval.SearchMode;
import in.edu.bennett.capstone.knowledge.retrieval.TestSearch;
import in.edu.bennett.capstone.knowledge.retrieval.TestSearch.Hit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * From a PDF to a search result: the chunks that real ingestion writes are found on the right page
 * in both modes (19.2), and the instruction prefix of the embedding model (ADR-033) reaches the
 * model with queries only, never with document text.
 */
class SearchAfterIngestionTest extends IngestionTestSupport {

  private static final String PASSAGE =
      "Laboratory goggles are mandatory near the chemical storage cabinets.";

  @Autowired EmbeddingProperties embedding;

  @Test
  void anIngestedPassageIsFoundOnItsPageAndOnlyTheQueryCarriesThePrefix() {
    List<String> page2 = new ArrayList<>(lines("page2marker", 15));
    page2.add(PASSAGE);
    page2.addAll(lines("page2marker", 15));
    UUID id =
        upload(
            TestPdfs.withPageLines(
                List.of(lines("page1marker", 30), page2, lines("page3marker", 30))));

    assertThat(processNext()).isEqualTo(Outcome.INDEXED);
    assertThat(embedding.queryPrefix()).isNotBlank();
    assertThat(model.requests())
        .isNotEmpty()
        .allSatisfy(
            texts -> assertThat(texts).noneMatch(text -> text.contains(embedding.queryPrefix())));
    model.reset();

    for (SearchMode mode : SearchMode.values()) {
      List<Hit> found = TestSearch.hits(mvc, faculty, mode, "goggles chemical storage", 3);
      assertThat(found)
          .as(mode.name())
          .first()
          .satisfies(
              hit -> {
                assertThat(hit.documentId()).isEqualTo(id);
                assertThat(hit.pageStart()).isLessThanOrEqualTo(2);
                assertThat(hit.pageEnd()).isGreaterThanOrEqualTo(2);
                assertThat(hit.text()).contains(PASSAGE);
              });
    }
    assertThat(model.requests())
        .containsExactly(List.of(embedding.queryPrefix() + "goggles chemical storage"));
  }
}
