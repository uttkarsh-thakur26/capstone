package in.edu.bennett.capstone.knowledge.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import in.edu.bennett.capstone.knowledge.AuditRows;
import in.edu.bennett.capstone.knowledge.TestAccounts;
import in.edu.bennett.capstone.knowledge.TestcontainersConfiguration;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * The audit log records the client's address, taken from {@code X-Forwarded-For} only when the
 * request comes from a proxy on a private or loopback address (ADR-024). This runs on a real port,
 * because Tomcat, not Spring MVC, reads the header.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ClientAddressTest {

  @LocalServerPort int port;

  @Autowired JdbcClient jdbc;

  @Test
  void theAddressSetByTheProxyIsRecorded() throws Exception {
    String userId = register("198.51.100.23");

    assertThat(AuditRows.forResource(jdbc, userId))
        .singleElement()
        .satisfies(row -> assertThat(row).containsEntry("ip_address", "198.51.100.23"));
  }

  @Test
  void withoutAProxyTheConnectionsAddressIsRecorded() throws Exception {
    String userId = register(null);

    assertThat(AuditRows.forResource(jdbc, userId))
        .singleElement()
        .satisfies(row -> assertThat(row).containsEntry("ip_address", "127.0.0.1"));
  }

  private String register(String forwardedFor) throws IOException, InterruptedException {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/auth/register"))
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    """
                    {"email": "%s", "displayName": "Proxy Test", "password": "%s"}
                    """
                        .formatted(TestAccounts.uniqueEmail(), TestAccounts.PASSWORD)));
    if (forwardedFor != null) {
      request.header("X-Forwarded-For", forwardedFor);
    }
    HttpResponse<String> response =
        HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(201);
    return JsonPath.read(response.body(), "$.id");
  }
}
