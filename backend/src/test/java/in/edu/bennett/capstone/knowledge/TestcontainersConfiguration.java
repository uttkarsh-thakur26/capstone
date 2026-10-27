package in.edu.bennett.capstone.knowledge;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

// Every context that starts the database also gets the fake models, never a real one (19.1).
@TestConfiguration(proxyBeanMethods = false)
@Import(FakeModelsConfiguration.class)
public class TestcontainersConfiguration {

  /**
   * The production database image, pgvector/pgvector:0.8.7-pg18-trixie, pinned by digest (ADR-015).
   * Testcontainers cannot parse "tag@digest", so the tag is named only here. compose.yaml uses the
   * same digest.
   */
  static final String POSTGRES_IMAGE =
      "pgvector/pgvector@sha256:9d9c930220cb9bf2f956d10a8f909cf9973d9672ca0278aef2c1e6facccad2e0";

  @Bean
  @ServiceConnection
  PostgreSQLContainer postgresContainer() {
    return new PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE));
  }
}
