package in.edu.bennett.capstone.knowledge.document;

/**
 * The kind of a document, picked from a fixed list instead of typed (DOC-1, ADR-040), so one kind
 * is always stored and shown the same way. The API and the database use the constant's name; the
 * screens show the label.
 */
public enum DocumentCategory {
  POLICY("Policy"),
  SYLLABUS("Syllabus"),
  COURSE_PLAN("Course plan"),
  EXAMINATION("Examination"),
  NOTICE("Notice"),
  SCHEDULE("Schedule"),
  HANDBOOK("Handbook"),
  OTHER("Other");

  private final String label;

  DocumentCategory(String label) {
    this.label = label;
  }

  /** The name a reader sees, such as "Course plan". */
  public String label() {
    return label;
  }
}
