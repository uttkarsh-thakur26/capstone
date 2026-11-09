package in.edu.bennett.capstone.knowledge.document;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A document title (DOC-1, ADR-040), judged as it is stored ({@link Validator#normalize}): 3 to 200
 * characters, at least 3 of them letters in any script, so "@@@###" or "1111" is refused. Null is
 * valid, so the same rule serves an edit that leaves the title alone; combine with {@code NotNull}
 * where a title is required. Control characters are {@code PlainText}'s rule.
 */
@Documented
@Constraint(validatedBy = DocumentTitle.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface DocumentTitle {

  int MIN_LENGTH = 3;
  int MAX_LENGTH = 200;
  int MIN_LETTERS = 3;

  String message() default "is not a valid title";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  class Validator implements ConstraintValidator<DocumentTitle, String> {

    /**
     * The title as it is stored: every run of white space, line breaks and Unicode spaces such as
     * U+00A0 included, replaced by one space, then stripped.
     */
    static String normalize(String title) {
      return title.replaceAll("[\\s\\p{Z}]+", " ").strip();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
      if (value == null) {
        return true;
      }
      String title = normalize(value);
      String problem = null;
      if (title.isEmpty()) {
        problem = "must not be blank";
      } else if (title.codePointCount(0, title.length()) < MIN_LENGTH
          || title.codePointCount(0, title.length()) > MAX_LENGTH) {
        problem = "must be " + MIN_LENGTH + " to " + MAX_LENGTH + " characters long";
      } else if (title.codePoints().filter(Character::isLetter).count() < MIN_LETTERS) {
        problem = "must contain at least " + MIN_LETTERS + " letters";
      }
      if (problem == null) {
        return true;
      }
      context.disableDefaultConstraintViolation();
      context.buildConstraintViolationWithTemplate(problem).addConstraintViolation();
      return false;
    }
  }
}
