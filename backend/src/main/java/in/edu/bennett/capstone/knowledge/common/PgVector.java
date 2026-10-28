package in.edu.bennett.capstone.knowledge.common;

/**
 * pgvector's text form of a vector, for example {@code [0.1,-0.25,3.0E-5]}. Vectors travel as a
 * bound parameter in this form and are cast in SQL ({@code CAST(? AS vector)}), never concatenated
 * into a statement (SEC-7), so no extra JDBC type library is needed.
 */
public final class PgVector {

  private PgVector() {}

  public static String text(float[] vector) {
    StringBuilder text = new StringBuilder(vector.length * 12).append('[');
    for (int i = 0; i < vector.length; i++) {
      if (i > 0) {
        text.append(',');
      }
      text.append(vector[i]);
    }
    return text.append(']').toString();
  }
}
