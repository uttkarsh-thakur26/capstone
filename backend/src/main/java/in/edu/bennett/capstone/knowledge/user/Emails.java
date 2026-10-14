package in.edu.bennett.capstone.knowledge.user;

import java.util.Locale;

/** AUTH-10: emails are trimmed and lower-cased before they are stored or compared. */
public final class Emails {

  private Emails() {}

  public static String normalize(String email) {
    return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
  }
}
