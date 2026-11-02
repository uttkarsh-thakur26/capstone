package in.edu.bennett.capstone.knowledge.conversation;

import in.edu.bennett.capstone.knowledge.common.PageResponse;
import in.edu.bennett.capstone.knowledge.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Conversations and asking in one (specification 15.2, 12), for every signed-in user. The user
 * comes from the authenticated principal (ACL-E4); another user's conversation answers 404 on every
 * endpoint (CONV-1).
 */
@RestController
class ConversationController {

  private final ConversationService conversations;

  ConversationController(ConversationService conversations) {
    this.conversations = conversations;
  }

  @PostMapping(path = "/api/v1/conversations", produces = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<ConversationResponse> create(@AuthenticationPrincipal CurrentUser user) {
    return ResponseEntity.status(HttpStatus.CREATED).body(conversations.create(user));
  }

  /** CONV-2: the user's own conversations, the most recently used first. */
  @GetMapping(path = "/api/v1/conversations", produces = MediaType.APPLICATION_JSON_VALUE)
  PageResponse<ConversationResponse> list(
      @AuthenticationPrincipal CurrentUser user,
      @RequestParam(defaultValue = "0") @Min(0) @Max(PageResponse.MAX_PAGE) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(PageResponse.MAX_SIZE) int size) {
    return conversations.list(user, page, size);
  }

  /** CONV-2, CONV-6: the messages with their citations. */
  @GetMapping(path = "/api/v1/conversations/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
  ConversationDetailResponse get(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
    return conversations.get(user, id);
  }

  @PatchMapping(
      path = "/api/v1/conversations/{id}",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  ConversationResponse rename(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID id,
      @Valid @RequestBody RenameConversationRequest request) {
    return conversations.rename(user, id, request.title());
  }

  @DeleteMapping("/api/v1/conversations/{id}")
  ResponseEntity<Void> delete(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
    conversations.delete(user, id);
    return ResponseEntity.noContent().build();
  }

  /** Asking always happens inside a conversation; there is no other ask endpoint (15.2). */
  @PostMapping(
      path = "/api/v1/conversations/{id}/messages",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  AnswerResponse ask(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID id,
      @Valid @RequestBody AskRequest request) {
    return conversations.ask(user, id, request);
  }
}
