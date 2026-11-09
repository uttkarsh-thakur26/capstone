package in.edu.bennett.capstone.knowledge.document;

import static in.edu.bennett.capstone.knowledge.TestAccounts.body;
import static in.edu.bennett.capstone.knowledge.TestAccounts.login;
import static in.edu.bennett.capstone.knowledge.TestAccounts.loginAsAdmin;
import static in.edu.bennett.capstone.knowledge.TestAccounts.registerAs;
import static in.edu.bennett.capstone.knowledge.TestAccounts.registerStudent;
import static in.edu.bennett.capstone.knowledge.document.TestDocuments.metadata;
import static in.edu.bennett.capstone.knowledge.document.TestDocuments.upload;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import in.edu.bennett.capstone.knowledge.AuditRows;
import in.edu.bennett.capstone.knowledge.IntegrationTest;
import in.edu.bennett.capstone.knowledge.TestAccounts.Account;
import in.edu.bennett.capstone.knowledge.TestAccounts.Session;
import in.edu.bennett.capstone.knowledge.TestPdfs;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.util.unit.DataSize;

/**
 * DOC-1 to DOC-3, ACL-3, ACL-5, D16 and the upload tests of 19.2: a valid upload succeeds, a
 * student cannot upload, a non-PDF renamed to .pdf, an oversized and an empty file are rejected,
 * and a duplicate returns 409.
 */
@IntegrationTest
class DocumentUploadTest {

  /** {@code app.storage.root} of the test profile, relative to the working directory. */
  private static final Path STORAGE = Path.of("target/test-files");

  @Autowired MockMvcTester mvc;

  @Autowired JdbcClient jdbc;

  private Account faculty;
  private Session facultySession;

  @BeforeEach
  void signInAsFaculty() {
    faculty = registerAs(mvc, jdbc, "FACULTY");
    facultySession = login(mvc, faculty);
  }

  @Test
  void facultyUploadsAPdfThatIsStoredUnderAGeneratedKey() throws Exception {
    byte[] pdf = TestPdfs.withPages("Exam rules " + UUID.randomUUID(), "Page two");

    MvcTestResult result =
        upload(
            mvc,
            facultySession,
            pdf,
            "Exam Policy.pdf",
            """
            {"title": "  Examination Policy 2026 ", "description": "Rules for exams",
             "category": "POLICY", "documentDate": "2026-07-01"}
            """);

    assertThat(result).hasStatus(HttpStatus.CREATED);
    String id = JsonPath.read(body(result), "$.id");
    assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION))
        .isEqualTo("/api/v1/documents/" + id);
    assertThat(result)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"title": "Examination Policy 2026", "description": "Rules for exams",
             "category": "POLICY", "documentDate": "2026-07-01",
             "originalFilename": "Exam Policy.pdf", "contentType": "application/pdf",
             "sizeBytes": %d, "pageCount": 2, "status": "UPLOADED", "archived": false,
             "owner": {"id": "%s", "displayName": "Test User"}, "canManage": true}
            """
                .formatted(pdf.length, faculty.id()));

    Map<String, Object> row =
        jdbc.sql("SELECT storage_key, sha256, owner_id::text AS owner FROM document WHERE id = :id")
            .param("id", UUID.fromString(id))
            .query()
            .singleRow();
    String key = (String) row.get("storage_key");
    assertThat(UUID.fromString(key)).hasToString(key);
    assertThat(STORAGE.resolve(key)).hasBinaryContent(pdf);
    assertThat(row.get("sha256"))
        .isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pdf)));
    assertThat(row.get("owner")).isEqualTo(faculty.id().toString());

    List<Map<String, Object>> audit = AuditRows.forResource(jdbc, id);
    assertThat(audit).hasSize(1);
    assertThat(audit.getFirst())
        .containsEntry("action", "DOCUMENT_UPLOADED")
        .containsEntry("outcome", "SUCCESS")
        .containsEntry("actor_user_id", faculty.id().toString())
        .containsEntry("resource_type", "DOCUMENT");
  }

  @Test
  void anAdminCanUpload() {
    MvcTestResult result =
        upload(mvc, loginAsAdmin(mvc), TestPdfs.unique(), "a.pdf", metadata("By admin", "[]"));

    assertThat(result).hasStatus(HttpStatus.CREATED);
  }

  @Test
  void aStudentCannotUploadAndTheRefusalIsAudited() {
    Account student = registerStudent(mvc);

    MvcTestResult result =
        upload(mvc, login(mvc, student), TestPdfs.unique(), "a.pdf", metadata("No", "[]"));

    assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
    assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("FORBIDDEN");
    assertThat(documentsOwnedBy(student.id())).isZero();
    assertThat(AuditRows.forActorEmail(jdbc, student.email()))
        .anySatisfy(
            entry ->
                assertThat(entry)
                    .containsEntry("action", "ACCESS_DENIED")
                    .containsEntry("outcome", "DENIED"));
  }

  @Test
  void anotherFormatRenamedToPdfIsRejected() {
    byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0, 0, 13};

    MvcTestResult result = upload(mvc, facultySession, png, "photo.pdf", metadata("Photo", "[]"));

    assertThat(result).hasStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("UNSUPPORTED_TYPE");
    assertThat(documentsOwnedBy(faculty.id())).isZero();
  }

  @Test
  void anEmptyFileIsRejected() {
    MvcTestResult result =
        upload(mvc, facultySession, new byte[0], "empty.pdf", metadata("Empty", "[]"));

    assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("EMPTY_FILE");
  }

  @Test
  void aFileAboveTheLimitIsRejected() {
    // The test profile sets the limit to 256KB.
    byte[] large = new byte[256 * 1024 + 1];
    byte[] header = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
    System.arraycopy(header, 0, large, 0, header.length);

    MvcTestResult result = upload(mvc, facultySession, large, "big.pdf", metadata("Big", "[]"));

    assertThat(result).hasStatus(HttpStatus.CONTENT_TOO_LARGE);
    assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("FILE_TOO_LARGE");
  }

  @Test
  void aPdfThatNeedsAPasswordIsRejected() {
    MvcTestResult result =
        upload(
            mvc,
            facultySession,
            TestPdfs.encrypted("user-secret"),
            "locked.pdf",
            metadata("Locked", "[]"));

    assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("PDF_ENCRYPTED");
    assertThat(documentsOwnedBy(faculty.id())).isZero();
  }

  @Test
  void theSameFileFromTheSameOwnerIsADuplicateButAnotherOwnerMayUploadIt() {
    byte[] pdf = TestPdfs.unique();
    MvcTestResult first = upload(mvc, facultySession, pdf, "a.pdf", metadata("First", "[]"));
    String firstId = JsonPath.read(body(first), "$.id");

    MvcTestResult again = upload(mvc, facultySession, pdf, "b.pdf", metadata("Again", "[]"));

    assertThat(again).hasStatus(HttpStatus.CONFLICT);
    assertThat(again)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"code": "DUPLICATE_DOCUMENT", "documentId": "%s"}
            """
                .formatted(firstId));
    assertThat(documentsOwnedBy(faculty.id())).isEqualTo(1);
    Session otherFaculty = login(mvc, registerAs(mvc, jdbc, "FACULTY"));
    assertThat(upload(mvc, otherFaculty, pdf, "a.pdf", metadata("Theirs", "[]")))
        .hasStatus(HttpStatus.CREATED);
  }

  @Test
  void aNewDocumentHasNoGrantsUnlessTheUploadNamesThem() {
    Account student = registerStudent(mvc);

    UUID plain = TestDocuments.uploaded(mvc, facultySession, "Plain", "[]");
    UUID shared =
        TestDocuments.uploaded(
            mvc,
            facultySession,
            "Shared",
            """
            [{"type": "ROLE", "role": "STUDENT"}, {"type": "USER", "userId": "%s"},
             {"type": "ROLE", "role": "STUDENT"}]
            """
                .formatted(student.id()));

    assertThat(grantsOf(plain)).isEmpty();
    assertThat(grantsOf(shared)).containsExactly("ROLE:STUDENT", "USER:" + student.id());
  }

  @Test
  void aGrantMustNameAnExistingUserOrAPermittedRole() {
    MvcTestResult unknownUser =
        upload(
            mvc,
            facultySession,
            TestPdfs.unique(),
            "a.pdf",
            metadata(
                "Unknown",
                """
                [{"type": "USER", "userId": "%s"}]
                """
                    .formatted(UUID.randomUUID())));
    MvcTestResult adminRole =
        upload(
            mvc,
            facultySession,
            TestPdfs.unique(),
            "a.pdf",
            metadata("Admin role", "[{\"type\": \"ROLE\", \"role\": \"ADMIN\"}]"));
    MvcTestResult both =
        upload(
            mvc,
            facultySession,
            TestPdfs.unique(),
            "a.pdf",
            metadata(
                "Both",
                """
                [{"type": "ROLE", "role": "STUDENT", "userId": "%s"}]
                """
                    .formatted(faculty.id())));

    assertThat(unknownUser).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(unknownUser).bodyJson().extractingPath("$.code").isEqualTo("UNKNOWN_GRANTEE");
    assertThat(adminRole).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(adminRole).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
    assertThat(both).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(documentsOwnedBy(faculty.id())).isZero();
  }

  @Test
  void aTitleIsRequired() {
    MvcTestResult result =
        upload(mvc, facultySession, TestPdfs.unique(), "a.pdf", "{\"title\": \"  \"}");

    assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("title");
  }

  @Test
  void theFilenameIsKeptOnlyAsCleanMetadata() {
    // A path, and a right-to-left override that would display "evil.pdf" as "evilfdp.exe".
    MvcTestResult result =
        upload(
            mvc,
            facultySession,
            TestPdfs.unique(),
            "..\\..\\uploads/evil\u202Efdp.exe\r\n.pdf",
            metadata("Name", "[]"));

    assertThat(result).hasStatus(HttpStatus.CREATED);
    assertThat(result).bodyJson().extractingPath("$.originalFilename").isEqualTo("evilfdp.exe.pdf");
  }

  @Test
  void theUploadLimitsAreTheServersOwn() {
    assertThat(TestDocuments.get(mvc, facultySession, "/api/v1/documents/upload-limits"))
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"maxFileSizeBytes": 262144, "contentTypes": ["application/pdf"]}
            """);
  }

  @Test
  void theDefaultLimitIs25Megabytes() {
    UploadProperties defaults =
        new Binder(new MapConfigurationPropertySource(Map.of()))
            .bindOrCreate("app.upload", UploadProperties.class);

    assertThat(defaults.maxFileSize()).isEqualTo(DataSize.ofMegabytes(25));
  }

  @Test
  void theSafeFilenameIsShortenedToTheColumnLimit() {
    String name = "a".repeat(300) + ".pdf";

    assertThat(DocumentService.safeFilename(name)).hasSize(255);
    assertThat(DocumentService.safeFilename(null)).isEqualTo("document.pdf");
    assertThat(DocumentService.safeFilename("C:\\Users\\x\\")).isEqualTo("document.pdf");
    assertThat(DocumentService.safeFilename("\u0000\u0007")).isEqualTo("document.pdf");
  }

  private long documentsOwnedBy(UUID owner) {
    return jdbc.sql("SELECT count(*) FROM document WHERE owner_id = :owner")
        .param("owner", owner)
        .query(Long.class)
        .single();
  }

  private List<String> grantsOf(UUID document) {
    return jdbc.sql(
            """
            SELECT grantee_type || ':' || coalesce(grantee_role, grantee_user_id::text)
            FROM document_grant WHERE document_id = :id ORDER BY grantee_type, grantee_role
            """)
        .param("id", document)
        .query(String.class)
        .list();
  }
}
