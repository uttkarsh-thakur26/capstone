package in.edu.bennett.capstone.knowledge.security;

import static in.edu.bennett.capstone.knowledge.assistant.TestSummaries.compare;
import static in.edu.bennett.capstone.knowledge.assistant.TestSummaries.indexed;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import in.edu.bennett.capstone.knowledge.FakeChatModel;
import in.edu.bennett.capstone.knowledge.IntegrationTest;
import in.edu.bennett.capstone.knowledge.TestAccounts;
import in.edu.bennett.capstone.knowledge.TestAccounts.Account;
import in.edu.bennett.capstone.knowledge.TestAccounts.Session;
import in.edu.bennett.capstone.knowledge.assistant.TestSummaries;
import in.edu.bennett.capstone.knowledge.conversation.TestConversations;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * SEC-8 and AUTH-5 through the real endpoints, with low limits in a context of its own: three AI
 * requests a minute per user, two failed sign-ins per email, two registrations per address. A
 * refused request answers 429 with {@code Retry-After} and never reaches the model. Every account
 * is registered from an address of its own, so the registration limit does not get in the way.
 */
@IntegrationTest
@TestPropertySource(
    properties = {
      "app.security.rate-limits.ai-requests=3",
      "app.security.rate-limits.login-email-failures=2",
      "app.security.rate-limits.login-address-failures=1000",
      "app.security.rate-limits.registration-requests=2"
    })
class RateLimitTest {

  private static final AtomicInteger ADDRESSES = new AtomicInteger();
  private static final String RULE = "Hostel gates close at 22:30 for every resident student.";
  private static final String QUESTION = "When do hostel gates close for every resident student?";

  @Autowired MockMvcTester mvc;

  @Autowired JdbcClient jdbc;

  @Autowired FakeChatModel chat;

  private Account account;
  private Session faculty;

  @BeforeEach
  void setUp() {
    chat.reset();
    account = register(nextAddress());
    jdbc.sql("UPDATE app_user SET role = 'FACULTY' WHERE id = :id")
        .param("id", account.id())
        .update();
    faculty = TestAccounts.login(mvc, account);
  }

  @AfterEach
  void resetTheModel() {
    chat.reset();
  }

  @Test
  void aQuestionBeyondTheBudgetIs429AndReachesNoModel() {
    indexed(mvc, jdbc, faculty, "Hostel rules " + UUID.randomUUID(), "[]", 20, RULE);
    for (int i = 0; i < 3; i++) {
      assertThat(TestConversations.answer(mvc, faculty, QUESTION).outcome()).isEqualTo("ANSWERED");
    }

    MvcTestResult refused =
        TestConversations.ask(mvc, faculty, TestConversations.create(mvc, faculty), QUESTION, null);

    assertThat(refused).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(refused).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    String body = TestAccounts.body(refused);
    assertThat((String) JsonPath.read(body, "$.code")).isEqualTo("RATE_LIMITED");
    long seconds = ((Number) JsonPath.read(body, "$.retryAfterSeconds")).longValue();
    // Three a minute: the next token comes at most 20 seconds later.
    assertThat(seconds).isBetween(1L, 20L);
    assertThat(refused.getResponse().getHeader(HttpHeaders.RETRY_AFTER))
        .isEqualTo(Long.toString(seconds));
    assertThat((String) JsonPath.read(body, "$.detail"))
        .isEqualTo("Too many requests. Try again in " + seconds + " seconds.");
    assertThat(chat.calls()).isEqualTo(3);
    assertThat(
            jdbc.sql("SELECT count(*) FROM query_log WHERE user_id = :id AND type = 'ASK'")
                .param("id", account.id())
                .query(Long.class)
                .single())
        .isEqualTo(3);
  }

  @Test
  void askingSummarizingAndComparingShareOneBudget() {
    UUID left = indexed(mvc, jdbc, faculty, "Rules A " + UUID.randomUUID(), "[]", 20, RULE);
    UUID right =
        indexed(
            mvc,
            jdbc,
            faculty,
            "Rules B " + UUID.randomUUID(),
            "[]",
            20,
            "Hostel gates close at 23:00 for every resident student.");

    assertThat(TestConversations.answer(mvc, faculty, QUESTION).outcome()).isEqualTo("ANSWERED");
    assertThat(compare(mvc, faculty, left, right)).hasStatusOk();
    assertThat(TestSummaries.request(mvc, faculty, left)).hasStatus(HttpStatus.ACCEPTED);

    assertThat(TestSummaries.request(mvc, faculty, right)).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(compare(mvc, faculty, right, left)).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
    // Reading a summary is not an AI request.
    assertThat(TestSummaries.get(mvc, faculty, left)).hasStatusOk();
  }

  @Test
  void anotherUsersBudgetIsUntouched() {
    for (int i = 0; i < 3; i++) {
      TestConversations.answer(mvc, faculty, "canteen menu fridays");
    }
    assertThat(
            TestConversations.ask(
                mvc, faculty, TestConversations.create(mvc, faculty), "canteen menu", null))
        .hasStatus(HttpStatus.TOO_MANY_REQUESTS);

    Session other = TestAccounts.login(mvc, register(nextAddress()));
    assertThat(TestConversations.answer(mvc, other, "canteen menu fridays").outcome())
        .isEqualTo("INSUFFICIENT_EVIDENCE");
  }

  @Test
  void signInIsRefusedForAnEmailAfterTwoFailuresEvenWithTheRightPassword() {
    Account target = register(nextAddress());
    assertThat(TestAccounts.loginRequest(mvc, target.email(), "wrong password one"))
        .hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(TestAccounts.loginRequest(mvc, target.email(), "wrong password two"))
        .hasStatus(HttpStatus.UNAUTHORIZED);

    MvcTestResult refused = TestAccounts.loginRequest(mvc, target.email(), TestAccounts.PASSWORD);

    assertThat(refused).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
    assertThat((String) JsonPath.read(TestAccounts.body(refused), "$.detail"))
        .startsWith("Too many failed sign-ins. Try again in ");
    // An unknown email is limited the same way, so a 429 never tells whether an account exists.
    String unknown = TestAccounts.uniqueEmail();
    TestAccounts.loginRequest(mvc, unknown, "wrong password one");
    TestAccounts.loginRequest(mvc, unknown, "wrong password two");
    assertThat(TestAccounts.loginRequest(mvc, unknown, "wrong password three"))
        .hasStatus(HttpStatus.TOO_MANY_REQUESTS);
  }

  @Test
  void aSuccessfulSignInDoesNotCount() {
    for (int i = 0; i < 4; i++) {
      assertThat(TestAccounts.loginRequest(mvc, account.email(), TestAccounts.PASSWORD))
          .hasStatusOk();
    }
  }

  @Test
  void registrationIsLimitedPerAddress() {
    String address = nextAddress();
    register(address);
    register(address);

    MvcTestResult refused = registerRequest(address, TestAccounts.uniqueEmail());

    assertThat(refused).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
    assertThat((String) JsonPath.read(TestAccounts.body(refused), "$.detail"))
        .isEqualTo("Too many registrations from this network. Try again in 30 minutes.");
    assertThat(registerRequest(nextAddress(), TestAccounts.uniqueEmail()))
        .hasStatus(HttpStatus.CREATED);
  }

  private Account register(String address) {
    String email = TestAccounts.uniqueEmail();
    MvcTestResult result = registerRequest(address, email);
    assertThat(result).hasStatus(HttpStatus.CREATED);
    return new Account(UUID.fromString(JsonPath.read(TestAccounts.body(result), "$.id")), email);
  }

  private MvcTestResult registerRequest(String address, String email) {
    return mvc.post()
        .uri("/api/v1/auth/register")
        .with(
            request -> {
              request.setRemoteAddr(address);
              return request;
            })
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            """
            {"email": "%s", "displayName": "Test User", "password": "%s"}
            """
                .formatted(email, TestAccounts.PASSWORD))
        .exchange();
  }

  private static String nextAddress() {
    int n = ADDRESSES.incrementAndGet();
    return "10.77." + (n / 256) + "." + (n % 256);
  }
}
