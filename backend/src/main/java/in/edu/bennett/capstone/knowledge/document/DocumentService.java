package in.edu.bennett.capstone.knowledge.document;

import in.edu.bennett.capstone.knowledge.audit.AuditAction;
import in.edu.bennett.capstone.knowledge.audit.AuditEvent;
import in.edu.bennett.capstone.knowledge.audit.AuditOutcome;
import in.edu.bennett.capstone.knowledge.audit.AuditService;
import in.edu.bennett.capstone.knowledge.common.ApiException;
import in.edu.bennett.capstone.knowledge.common.PageResponse;
import in.edu.bennett.capstone.knowledge.security.CurrentUser;
import in.edu.bennett.capstone.knowledge.storage.FileDeletionQueue;
import in.edu.bennett.capstone.knowledge.storage.FileStorage;
import in.edu.bennett.capstone.knowledge.user.UserService;
import in.edu.bennett.capstone.knowledge.user.UserSummary;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * Documents: the {@code document} module's API (specification 8). Every method that takes a
 * document id finds it through the readable predicate first (ACL-E1), so a document the user cannot
 * read is never loaded and answers like one that does not exist (ACL-8).
 *
 * <p>No transaction is held across file I/O (4.5): methods that touch files run their database work
 * in short transactions of their own.
 */
@Service
public class DocumentService {

  private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

  /** The {@code resource_type} of audit entries about documents. */
  public static final String RESOURCE_DOCUMENT = "DOCUMENT";

  /** The one type accepted in T1 (D9). It is set by the server, never taken from the client. */
  static final String PDF = MediaType.APPLICATION_PDF_VALUE;

  private static final int MAX_FILENAME_LENGTH = 255;

  private final DocumentRepository documents;
  private final DocumentGrantRepository grants;
  private final DocumentAccessPolicy policy;
  private final FileStorage storage;
  private final FileDeletionQueue deletions;
  private final UserService users;
  private final AuditService audit;
  private final UploadProperties upload;
  private final TransactionTemplate transactions;

  DocumentService(
      DocumentRepository documents,
      DocumentGrantRepository grants,
      DocumentAccessPolicy policy,
      FileStorage storage,
      FileDeletionQueue deletions,
      UserService users,
      AuditService audit,
      UploadProperties upload,
      PlatformTransactionManager transactionManager) {
    this.documents = documents;
    this.grants = grants;
    this.policy = policy;
    this.storage = storage;
    this.deletions = deletions;
    this.users = users;
    this.audit = audit;
    this.upload = upload;
    this.transactions = new TransactionTemplate(transactionManager);
  }

  UploadLimitsResponse uploadLimits() {
    return new UploadLimitsResponse(upload.maxFileSize().toBytes(), List.of(PDF));
  }

  /**
   * DOC-1 to DOC-3, ACL-3, ACL-5: checks the file, stores it and creates the document as {@code
   * UPLOADED}, owned by the uploader. ADMIN and FACULTY only (D16), by URL rule and here.
   *
   * <p>The file is checked and stored without a transaction; the row, its grants and the audit
   * entry are then written in one short transaction (ADR-031). If that fails, the stored file is
   * removed again.
   */
  @PreAuthorize("hasAnyRole('ADMIN', 'FACULTY')")
  public DocumentResponse upload(
      CurrentUser user, MultipartFile file, UploadDocumentRequest request) {
    if (file.getSize() > upload.maxFileSize().toBytes()) {
      throw new ApiException(
          HttpStatus.CONTENT_TOO_LARGE,
          "FILE_TOO_LARGE",
          "The file is larger than the upload limit of "
              + upload.maxFileSize().toMegabytes()
              + " MB.");
    }
    Set<Grantee> grantees = grantees(request.grants());
    Path temp = createTempFile();
    try {
      file.transferTo(temp);
      int pageCount = PdfCheck.pageCount(temp);
      String sha256 = sha256(temp);
      documents
          .findByOwnerIdAndSha256(user.id(), sha256)
          .ifPresent(
              existing -> {
                throw duplicate(existing.getId());
              });
      String key;
      try (InputStream content = Files.newInputStream(temp)) {
        key = storage.store(content);
      }
      StoredUpload stored =
          new StoredUpload(
              safeFilename(file.getOriginalFilename()),
              PDF,
              Files.size(temp),
              sha256,
              key,
              pageCount);
      return insert(user, request.metadata(), stored, grantees);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } finally {
      deleteQuietly(temp);
    }
  }

  private DocumentResponse insert(
      CurrentUser user, DocumentMetadata metadata, StoredUpload file, Set<Grantee> grantees) {
    try {
      return transactions.execute(
          status -> {
            Document document = documents.saveAndFlush(new Document(user.id(), metadata, file));
            grants.saveAll(
                grantees.stream()
                    .map(grantee -> new DocumentGrant(document.getId(), grantee, user.id()))
                    .toList());
            audit.record(
                event(AuditAction.DOCUMENT_UPLOADED, AuditOutcome.SUCCESS, user, document.getId())
                    .withDetails(
                        Map.of(
                            "title", document.getTitle(),
                            "sizeBytes", document.getSizeBytes(),
                            "grants", describe(grantees))));
            log.info("User {} uploaded document {}", user.id(), document.getId());
            return DocumentResponse.of(document, user.displayName(), true);
          });
    } catch (RuntimeException e) {
      deletions.deleteOrRetryLater(file.storageKey());
      if (e instanceof DataIntegrityViolationException) {
        // The same file uploaded twice at the same moment: the unique constraint decided (DOC-2).
        documents
            .findByOwnerIdAndSha256(user.id(), file.sha256())
            .ifPresent(
                existing -> {
                  throw duplicate(existing.getId());
                });
      }
      throw e;
    }
  }

  /** Columns the document list may be sorted by: API name to entity property (SEC-7). */
  private static final Map<String, String> SORTABLE =
      Map.of(
          "title", "title",
          "category", "category",
          "documentDate", "documentDate",
          "status", "status",
          "createdAt", "createdAt");

  /**
   * DOC-4: the documents the user can read, filtered, sorted and paged. One query returns the page
   * and one counts the matches; both carry the readable predicate, so the total never counts a
   * document the user cannot read (ACL-E2, ACL-8).
   */
  @Transactional(readOnly = true)
  public PageResponse<DocumentSummaryResponse> list(
      CurrentUser user, DocumentQuery query, int page, int size, String sort) {
    Page<Document> found =
        documents.findAll(
            policy.readableBy(user).and(query.toSpecification(user.id())),
            PageResponse.request(
                page,
                size,
                sort,
                SORTABLE,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id"))));
    Map<UUID, UserSummary> owners = users.findSummaries(found.map(Document::getOwnerId).toSet());
    return PageResponse.of(
        found.map(document -> DocumentSummaryResponse.of(document, ownerName(owners, document))));
  }

  /** DOC-5: the detail of a document the user can read. */
  @Transactional(readOnly = true)
  public DocumentResponse get(CurrentUser user, UUID id) {
    Document document = readable(user, id);
    return response(document, user);
  }

  /**
   * DOC-6: opens the file of a document the user can read and records the download. The access
   * check runs in its own short read transaction, and no transaction is open while the file is read
   * (4.5). The caller streams and closes the content.
   */
  DocumentFile download(CurrentUser user, UUID id) {
    Document document = readable(user, id);
    InputStream content = storage.open(document.getStorageKey());
    try {
      audit.record(
          event(AuditAction.DOCUMENT_DOWNLOADED, AuditOutcome.SUCCESS, user, document.getId()));
    } catch (RuntimeException e) {
      closeQuietly(content);
      throw e;
    }
    return new DocumentFile(
        content,
        document.getOriginalFilename(),
        document.getContentType(),
        document.getSizeBytes());
  }

  /**
   * ACL-E1, ACL-8: the document, found with the readable predicate in the same query. A document
   * that exists but is not readable is never loaded, so it fails exactly like a missing one: the
   * same query, the same 404, and no audit entry (ADR-024).
   */
  private Document readable(CurrentUser user, UUID id) {
    return documents
        .findOne(
            policy.readableBy(user).and((document, query, cb) -> cb.equal(document.get("id"), id)))
        .orElseThrow(DocumentService::notFound);
  }

  private DocumentResponse response(Document document, CurrentUser user) {
    String ownerName =
        document.getOwnerId().equals(user.id())
            ? user.displayName()
            : ownerName(users.findSummaries(Set.of(document.getOwnerId())), document);
    return DocumentResponse.of(document, ownerName, policy.canManage(document, user));
  }

  private static String ownerName(Map<UUID, UserSummary> owners, Document document) {
    UserSummary owner = owners.get(document.getOwnerId());
    // Owners cannot be deleted (ADR-030), so this is only a guard.
    return owner == null ? "Unknown user" : owner.displayName();
  }

  static ApiException notFound() {
    return new ApiException(
        HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested resource does not exist.");
  }

  /**
   * The grants of a request, without duplicates. Every named user must exist; a grant to a missing
   * user would silently grant nothing.
   */
  private Set<Grantee> grantees(List<GrantRequest> requested) {
    if (requested == null || requested.isEmpty()) {
      return Set.of();
    }
    Set<Grantee> grantees =
        requested.stream()
            .map(GrantRequest::toGrantee)
            .sorted(Grantee.ORDER)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    Set<UUID> userIds =
        grantees.stream()
            .filter(grantee -> grantee.type() == GranteeType.USER)
            .map(Grantee::userId)
            .collect(Collectors.toSet());
    if (!users.findSummaries(userIds).keySet().containsAll(userIds)) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "UNKNOWN_GRANTEE", "A grant names a user who does not exist.");
    }
    return grantees;
  }

  private static List<String> describe(Set<Grantee> grantees) {
    return grantees.stream().sorted(Grantee.ORDER).map(Grantee::describe).toList();
  }

  private static AuditEvent event(
      AuditAction action, AuditOutcome outcome, CurrentUser user, UUID documentId) {
    return AuditEvent.of(action, outcome, user.id(), user.email())
        .withResource(RESOURCE_DOCUMENT, documentId);
  }

  private static ApiException duplicate(UUID existingId) {
    return new ApiException(
        HttpStatus.CONFLICT,
        "DUPLICATE_DOCUMENT",
        "You have already uploaded this file.",
        Map.of("documentId", existingId));
  }

  /**
   * The user's filename as metadata only (DOC-2): no directory part, no control or formatting
   * characters (which could hide the real extension or break a header), at most 255 characters.
   */
  static String safeFilename(String original) {
    String name = original == null ? "" : original;
    name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
    name = name.replaceAll("[\\p{Cc}\\p{Cf}]", "").strip();
    if (name.codePointCount(0, name.length()) > MAX_FILENAME_LENGTH) {
      name = name.substring(0, name.offsetByCodePoints(0, MAX_FILENAME_LENGTH));
    }
    return name.isEmpty() ? "document.pdf" : name;
  }

  private static String sha256(Path file) {
    MessageDigest digest = sha256Digest();
    try (InputStream in = new DigestInputStream(Files.newInputStream(file), digest)) {
      in.transferTo(OutputStream.nullOutputStream());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  private static MessageDigest sha256Digest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Every Java runtime has SHA-256", e);
    }
  }

  private static Path createTempFile() {
    try {
      return Files.createTempFile("upload-", ".pdf");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void closeQuietly(InputStream content) {
    try {
      content.close();
    } catch (IOException e) {
      log.warn("Could not close a stored file", e);
    }
  }

  private static void deleteQuietly(Path file) {
    try {
      Files.deleteIfExists(file);
    } catch (IOException e) {
      log.warn("Could not remove the temporary upload {}", file, e);
    }
  }
}
