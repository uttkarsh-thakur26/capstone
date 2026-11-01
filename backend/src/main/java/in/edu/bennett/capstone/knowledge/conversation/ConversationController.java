package in.edu.bennett.capstone.knowledge.conversation;

import in.edu.bennett.capstone.knowledge.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Creating a conversation and asking in it (specification 15.2; S15, ADR-037), for every signed-in
 * user. The user comes from the authenticated principal (ACL-E4). Listing, opening, renaming and
 * deleting arrive in Phase 6.
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
