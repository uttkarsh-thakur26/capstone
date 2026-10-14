package in.edu.bennett.capstone.knowledge.user;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Runs once at startup. If it throws, the application does not start, which is the intent: a
 * deployment without an admin could never promote anyone (AUTH-3).
 */
@Component
class AdminBootstrap implements ApplicationRunner {

  private final UserService users;
  private final BootstrapAdminProperties admin;

  AdminBootstrap(UserService users, BootstrapAdminProperties admin) {
    this.users = users;
    this.admin = admin;
  }

  @Override
  public void run(ApplicationArguments args) {
    users.bootstrapAdmin(admin);
  }
}
