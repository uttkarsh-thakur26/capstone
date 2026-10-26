package in.edu.bennett.capstone.knowledge.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import in.edu.bennett.capstone.knowledge.IntegrationTest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The constraints of the Phase 2 tables (specification 7.2, ADR-030), checked in the database. */
@IntegrationTest
class DocumentSchemaTest {

  private static final OffsetDateTime NOW = OffsetDateTime.now(ZoneOffset.UTC);

  @Autowired JdbcClient jdbc;

  private UUID owner;
  private UUID otherUser;

  @BeforeEach
  void createUsers() {
    owner = insertUser();
    otherUser = insertUser();
  }

  @Test
  void aRoleCanBeGrantedOnlyOncePerDocument() {
    UUID document = insertDocument(owner, "UPLOADED", null);
    insertRoleGrant(document, "STUDENT");

    assertThatThrownBy(() -> insertRoleGrant(document, "STUDENT"))
        .isInstanceOf(DataIntegrityViolationException.class);
    insertRoleGrant(document, "FACULTY");
  }

  @Test
  void aUserCanBeGrantedOnlyOncePerDocument() {
    UUID document = insertDocument(owner, "UPLOADED", null);
    insertUserGrant(document, otherUser);

    assertThatThrownBy(() -> insertUserGrant(document, otherUser))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void aGrantNamesExactlyOneGranteeAndNeverTheAdminRole() {
    UUID document = insertDocument(owner, "UPLOADED", null);

    assertThatThrownBy(
            () ->
                jdbc.sql(
                        """
                        INSERT INTO document_grant (id, document_id, grantee_type, grantee_role,
                                                    grantee_user_id, created_at)
                        VALUES (:id, :document, 'ROLE', 'STUDENT', :user, :now)
                        """)
                    .param("id", UUID.randomUUID())
                    .param("document", document)
                    .param("user", otherUser)
                    .param("now", NOW)
                    .update())
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> insertRoleGrant(document, "ADMIN"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void onlyAFailedDocumentCarriesAFailureCodeAndItAlwaysDoes() {
    insertDocument(owner, "FAILED", "ENCRYPTED");
    insertDocument(owner, "FAILED", "PROCESSING_TIMEOUT");

    assertThatThrownBy(() -> insertDocument(owner, "FAILED", null))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> insertDocument(owner, "INDEXED", "INTERNAL"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> insertDocument(owner, "FAILED", "SOMETHING_ELSE"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void theSameOwnerCannotStoreTheSameContentTwice() {
    String sha256 = randomSha256();
    insertDocument(owner, sha256);

    assertThatThrownBy(() -> insertDocument(owner, sha256))
        .isInstanceOf(DataIntegrityViolationException.class);
    insertDocument(otherUser, sha256);
  }

  @Test
  void deletingADocumentDeletesItsGrantsButAnOwnerCannotBeDeleted() {
    UUID document = insertDocument(owner, "UPLOADED", null);
    insertRoleGrant(document, "STUDENT");
    insertUserGrant(document, otherUser);

    assertThatThrownBy(
            () -> jdbc.sql("DELETE FROM app_user WHERE id = :id").param("id", owner).update())
        .isInstanceOf(DataIntegrityViolationException.class);

    jdbc.sql("DELETE FROM document WHERE id = :id").param("id", document).update();
    assertThat(
            jdbc.sql("SELECT count(*) FROM document_grant WHERE document_id = :id")
                .param("id", document)
                .query(Long.class)
                .single())
        .isZero();
  }

  private UUID insertUser() {
    UUID id = UUID.randomUUID();
    jdbc.sql(
            """
            INSERT INTO app_user (id, email, display_name, password_hash, role, enabled,
                                  created_at, updated_at)
            VALUES (:id, :email, 'Schema Test', 'not-a-hash', 'FACULTY', true, :now, :now)
            """)
        .param("id", id)
        .param("email", "schema-" + id + "@example.org")
        .param("now", NOW)
        .update();
    return id;
  }

  private UUID insertDocument(UUID ownerId, String status, String failureCode) {
    return insertDocument(ownerId, status, failureCode, randomSha256());
  }

  private UUID insertDocument(UUID ownerId, String sha256) {
    return insertDocument(ownerId, "UPLOADED", null, sha256);
  }

  private UUID insertDocument(UUID ownerId, String status, String failureCode, String sha256) {
    UUID id = UUID.randomUUID();
    jdbc.sql(
            """
            INSERT INTO document (id, owner_id, title, original_filename, content_type,
                                  size_bytes, sha256, storage_key, status, failure_code,
                                  archived, processing_attempts, row_version,
                                  created_at, updated_at)
            VALUES (:id, :owner, 'Schema test', 'test.pdf', 'application/pdf', 10, :sha256,
                    :key, :status, :failureCode, false, 0, 0, :now, :now)
            """)
        .param("id", id)
        .param("owner", ownerId)
        .param("sha256", sha256)
        .param("key", UUID.randomUUID().toString())
        .param("status", status)
        .param("failureCode", failureCode)
        .param("now", NOW)
        .update();
    return id;
  }

  private void insertRoleGrant(UUID document, String role) {
    jdbc.sql(
            """
            INSERT INTO document_grant (id, document_id, grantee_type, grantee_role, created_at)
            VALUES (:id, :document, 'ROLE', :role, :now)
            """)
        .param("id", UUID.randomUUID())
        .param("document", document)
        .param("role", role)
        .param("now", NOW)
        .update();
  }

  private void insertUserGrant(UUID document, UUID user) {
    jdbc.sql(
            """
            INSERT INTO document_grant (id, document_id, grantee_type, grantee_user_id, created_at)
            VALUES (:id, :document, 'USER', :user, :now)
            """)
        .param("id", UUID.randomUUID())
        .param("document", document)
        .param("user", user)
        .param("now", NOW)
        .update();
  }

  private static String randomSha256() {
    return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "");
  }
}
