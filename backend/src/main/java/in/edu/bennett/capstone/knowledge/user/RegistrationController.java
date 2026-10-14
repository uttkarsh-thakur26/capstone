package in.edu.bennett.capstone.knowledge.user;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class RegistrationController {

  private final UserService users;

  RegistrationController(UserService users) {
    this.users = users;
  }

  /** Public (AUTH-8). Creates a STUDENT; any role in the body is ignored (AUTH-1). */
  @PostMapping(
      path = "/api/v1/auth/register",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  UserResponse register(@Valid @RequestBody RegistrationRequest request) {
    return users.register(request);
  }
}
