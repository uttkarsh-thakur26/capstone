package in.edu.bennett.capstone.knowledge.security;

import in.edu.bennett.capstone.knowledge.user.Role;
import in.edu.bennett.capstone.knowledge.user.UserCredentials;
import in.edu.bennett.capstone.knowledge.user.UserService;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Checks an email and password with Spring Security's {@link DaoAuthenticationProvider}, which
 * hashes the password even for an unknown email, so the response time does not reveal whether an
 * account exists (AUTH-5).
 *
 * <p>The provider is private to this class rather than a bean: a {@code UserDetailsService} or
 * {@code AuthenticationProvider} bean would also be installed as Spring Security's global
 * authentication manager behind the bearer-token filter, where it has no use.
 */
@Component
class PasswordAuthenticator {

  private final AuthenticationManager manager;

  PasswordAuthenticator(UserService users, PasswordEncoder passwordEncoder) throws Exception {
    DaoAuthenticationProvider provider =
        new DaoAuthenticationProvider(
            email ->
                users
                    .findCredentials(email)
                    .map(AccountDetails::new)
                    .orElseThrow(() -> new UsernameNotFoundException("unknown")));
    provider.setPasswordEncoder(passwordEncoder);
    // The default checks the enabled flag before the password, so a disabled account would answer
    // faster than a wrong password and reveal that it exists. Checking it afterwards costs the
    // same hash in every case; both failures give the same response.
    provider.setPreAuthenticationChecks(user -> {});
    provider.setPostAuthenticationChecks(new AccountStatusUserDetailsChecker());
    provider.afterPropertiesSet();
    this.manager = new ProviderManager(provider);
  }

  /**
   * @return the signed-in user's id
   * @throws AuthenticationException for a wrong password, an unknown email or a disabled account
   */
  UUID authenticate(String email, String password) {
    AccountDetails account =
        (AccountDetails)
            manager
                .authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email, password))
                .getPrincipal();
    return account.id;
  }

  /** The account as Spring Security's provider sees it during one sign-in. */
  private static final class AccountDetails implements UserDetails, CredentialsContainer {

    private final UUID id;
    private final String email;
    private final Role role;
    private final boolean enabled;
    private String passwordHash;

    AccountDetails(UserCredentials credentials) {
      this.id = credentials.id();
      this.email = credentials.email();
      this.role = credentials.role();
      this.enabled = credentials.enabled();
      this.passwordHash = credentials.passwordHash();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
      return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
      return passwordHash;
    }

    @Override
    public String getUsername() {
      return email;
    }

    @Override
    public boolean isEnabled() {
      return enabled;
    }

    @Override
    public void eraseCredentials() {
      passwordHash = null;
    }
  }
}
