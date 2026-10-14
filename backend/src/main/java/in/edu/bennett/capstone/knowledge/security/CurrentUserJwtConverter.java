package in.edu.bennett.capstone.knowledge.security;

import in.edu.bennett.capstone.knowledge.user.UserService;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

/**
 * Turns a valid access token into the current user. The token proves only who the user is; role and
 * enabled flag come from one primary-key lookup on every request, not from the token (AUTH-7), so a
 * demotion or a disabled account applies to the next request (ACL-7).
 *
 * <p>Created in {@link JwtConfig}, not found by component scanning: web-slice tests scan every
 * {@code Converter} and would then need the whole user module.
 */
class CurrentUserJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

  private final UserService users;

  CurrentUserJwtConverter(UserService users) {
    this.users = users;
  }

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    UUID id;
    try {
      id = UUID.fromString(jwt.getSubject());
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new InvalidBearerTokenException("Invalid token");
    }
    CurrentUser user =
        users
            .findEnabled(id)
            .map(CurrentUser::of)
            // A disabled or missing account gets the same 401 as a bad token.
            .orElseThrow(() -> new InvalidBearerTokenException("Invalid token"));
    return UsernamePasswordAuthenticationToken.authenticated(user, null, user.authorities());
  }
}
