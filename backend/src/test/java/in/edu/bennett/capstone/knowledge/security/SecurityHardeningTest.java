package in.edu.bennett.capstone.knowledge.security;

import static org.assertj.core.api.Assertions.assertThat;

import in.edu.bennett.capstone.knowledge.IntegrationTest;
import in.edu.bennett.capstone.knowledge.TestAccounts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Gaps found in the Section 17 review of Phase 9 (ADR-041), each kept closed by a test. */
@IntegrationTest
class SecurityHardeningTest {

  @Autowired MockMvcTester mvc;

  @Autowired JdbcClient jdbc;

  /** SEC-6: an API response cannot be rendered as a page or framed. */
  @Test
  void apiResponsesForbidRenderingAndFraming() {
    MvcTestResult result = mvc.get().uri("/api/v1/meta").exchange();

    assertThat(result).hasStatusOk();
    assertThat(result)
        .headers()
        .hasValue("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'")
        .hasValue("X-Content-Type-Options", "nosniff")
        .hasValue("X-Frame-Options", "DENY");
  }

  /** AUD-2: a password typed into the email field is refused and never stored. */
  @Test
  void aPasswordTypedAsTheEmailIsNeverRecorded() {
    String typed = "my-secret-passphrase-" + System.nanoTime();

    MvcTestResult result = TestAccounts.loginRequest(mvc, typed, "anything at all");

    assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
    assertThat(
            jdbc.sql("SELECT count(*) FROM audit_log WHERE actor_email LIKE :typed")
                .param("typed", "%" + typed + "%")
                .query(Long.class)
                .single())
        .isZero();
  }
}
