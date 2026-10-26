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
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
   * DOC-7, DOC-11: replaces the metadata, archives or brings back a document, or both. Archiving
   * and bringing back are both recorded as {@code DOCUMENT_ARCHIVED} with the new value.
   */
  @Transactional
  public DocumentResponse update(CurrentUser user, UUID id, UpdateDocumentRequest change) {
    DocumentMetadata after = change.metadata();
    Document document =
        manageable(
            user,
            id,
            (after != null ? AuditAction.DOCUMENT_UPDATED : AuditAction.DOCUMENT_ARCHIVED).name());
    if (after != null) {
      List<String> changed = changedFields(metadataOf(document), after);
      if (!changed.isEmpty()) {
        document.describe(after);
        audit.record(
            event(AuditAction.DOCUMENT_UPDATED, AuditOutcome.SUCCESS, user, id)
                .withDetails(Map.of("changed", changed)));
      }
    }
    if (change.archived() != null && change.archived() != document.isArchived()) {
      document.setArchived(change.archived());
      audit.record(
          event(AuditAction.DOCUMENT_ARCHIVED, AuditOutcome.SUCCESS, user, id)
              .withDetails(Map.of("archived", change.archived())));
    }
    // Writes now, so a concurrent change fails here on row_version (DATA-3) and the response
    // carries the new updatedAt.
    documents.flush();
    log.info("User {} updated document {}", user.id(), id);
    return response(document, user);
  }

  /**
   * DOC-10: sends a {@code FAILED} or {@code INDEXED} document back for processing, with a fresh
   * attempt counter (ADR-030). One that is {@code UPLOADED} or {@code PROCESSING} is already queued
   * or running, so the request is refused rather than starting a second run.
   */
  @Transactional
  public DocumentResponse reprocess(CurrentUser user, UUID id) {
    Document document = manageable(user, id, AuditAction.DOCUMENT_REPROCESSED.name());
    DocumentStatus from = document.getStatus();
    if (!from.canBeReprocessed()) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "INVALID_STATUS",
          "Only a document that is indexed or failed can be processed again; this one is "
              + from.name().toLowerCase(Locale.ROOT)
              + ".");
    }
    document.reprocess();
    audit.record(
        event(AuditAction.DOCUMENT_REPROCESSED, AuditOutcome.SUCCESS, user, id)
            .withDetails(Map.of("from", from.name())));
    documents.flush();
    log.info("User {} sent document {} back for processing", user.id(), id);
    return response(document, user);
  }

  /**
   * DOC-9, DATA-2: removes the document's rows in one transaction (its grants go with it through
   * the foreign key), together with its audit entry and the record that its file must go. The file
   * is deleted after the commit, outside the transaction; if that fails, the cleanup task retries
   * it (ADR-031).
   */
  public void delete(CurrentUser user, UUID id) {
    String storageKey =
        transactions.execute(
            status -> {
              Document document = manageable(user, id, AuditAction.DOCUMENT_DELETED.name());
              documents.delete(document);
              documents.flush();
              deletions.enqueue(document.getStorageKey());
              audit.record(
                  event(AuditAction.DOCUMENT_DELETED, AuditOutcome.SUCCESS, user, id)
                      .withDetails(Map.of("title", document.getTitle())));
              return document.getStorageKey();
            });
    deletions.deleteEnqueued(storageKey);
    log.info("User {} deleted document {}", user.id(), id);
  }

  /**
   * ACL-2 after ACL-E1: a document the user cannot read is a 404 like any missing one; one they can
   * read but not manage is a 403, recorded as {@code ACCESS_DENIED} (ADR-030). The entry is written
   * in its own transaction, so the refused request's rollback keeps it.
   */
  private Document manageable(CurrentUser user, UUID id, String attempted) {
    Document document = readable(user, id);
    if (!policy.canManage(document, user)) {
      audit.recordSeparately(
          event(AuditAction.ACCESS_DENIED, AuditOutcome.DENIED, user, id)
              .withDetails(Map.of("attempted", attempted)));
      throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You are not allowed to do this.");
    }
    return document;
  }

  private static DocumentMetadata metadataOf(Document document) {
    return new DocumentMetadata(
        document.getTitle(),
        document.getDescription(),
        document.getCategory(),
        document.getDocumentDate());
  }

  /** The names of the fields that differ, for the audit entry. Never their values. */
  private static List<String> changedFields(DocumentMetadata before, DocumentMetadata after) {
    List<String> changed = new ArrayList<>();
    if (!Objects.equals(before.title(), after.title())) {
      changed.add("title");
    }
    if (!Objects.equals(before.description(), after.description())) {
      changed.add("description");
    }
    if (!Objects.equals(before.category(), after.category())) {
      changed.add("category");
    }
    if (!Objects.equals(before.documentDate(), after.documentDate())) {
      changed.add("documentDate");
    }
    return changed;
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

  /** DOC-8: the grants of a document, for its owner and admins only (ACL-2). */
  @Transactional(readOnly = true)
  public GrantsResponse grants(CurrentUser user, UUID id) {
    manageable(user, id, "GRANTS_READ");
    return grantsResponse(
        grants.findByDocumentId(id).stream()
            .map(DocumentGrant::grantee)
            .sorted(Grantee.ORDER)
            .toList());
  }

  /**
   * DOC-8: replaces the whole set of grants. The change applies to the next request of every user,
   * because access is checked on every query and never cached (ACL-7). The document's row is
   * locked, so two replacements of one document's grants run one after the other.
   */
  @Transactional
  public GrantsResponse replaceGrants(CurrentUser user, UUID id, UpdateGrantsRequest request) {
    manageable(user, id, AuditAction.GRANTS_CHANGED.name());
    documents.lockById(id);
    Set<Grantee> after = grantees(request.grants());
    Set<Grantee> before =
        grants.findByDocumentId(id).stream()
            .map(DocumentGrant::grantee)
            .collect(Collectors.toSet());
    if (!before.equals(after)) {
      grants.deleteByDocumentId(id);
      grants.saveAllAndFlush(
          after.stream().map(grantee -> new DocumentGrant(id, grantee, user.id())).toList());
      audit.record(
          event(AuditAction.GRANTS_CHANGED, AuditOutcome.SUCCESS, user, id)
              .withDetails(Map.of("before", describe(before), "after", describe(after))));
      log.info("User {} changed the grants of document {}", user.id(), id);
    }
    return grantsResponse(after.stream().sorted(Grantee.ORDER).toList());
  }

  private GrantsResponse grantsResponse(List<Grantee> grantees) {
    Map<UUID, UserSummary> people =
        users.findSummaries(
            grantees.stream()
                .filter(grantee -> grantee.type() == GranteeType.USER)
                .map(Grantee::userId)
                .toList());
    return new GrantsResponse(
        grantees.stream()
            .map(
                grantee ->
                    new GrantResponse(
                        grantee.type(),
                        grantee.role(),
                        grantee.userId() == null ? null : people.get(grantee.userId())))
            .toList());
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
