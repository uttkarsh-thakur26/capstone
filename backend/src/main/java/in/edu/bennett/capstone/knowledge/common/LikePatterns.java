package in.edu.bennett.capstone.knowledge.common;

import java.util.Locale;

/**
 * SQL {@code LIKE} patterns from user input. The wildcard characters in the input are matched
 * literally, with {@link #ESCAPE} as the escape character, so typing {@code %} finds a percent sign
 * instead of everything.
 */
public final class LikePatterns {

  public static final char ESCAPE = '\\';

  private LikePatterns() {}

  /** A pattern that matches any lower-cased value containing the stripped, lower-cased text. */
  public static String containing(String text) {
    String value = text.strip().toLowerCase(Locale.ROOT);
    return "%" + value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
  }
}
