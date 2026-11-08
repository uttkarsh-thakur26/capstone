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
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * A document's own date (DOC-1, ADR-040): from 1 January 2000 to one year after today, because a
 * notice may take effect in the future but not decades ahead. Today is the server's date in UTC,
 * plus one day, so a reader in a zone ahead of UTC, such as India, can always pick the date one
 * year after their own today. A date that does not exist, such as 30 February, never gets this far:
 * the JSON reader refuses it, and the answer names the field. Null is valid.
 */
@Documented
@Constraint(validatedBy = DocumentDate.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface DocumentDate {

  LocalDate EARLIEST = LocalDate.of(2000, 1, 1);

  String message() default "is not a valid date";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  /** Created by Spring's validator factory, which supplies the clock. */
  class Validator implements ConstraintValidator<DocumentDate, LocalDate> {

    private final Clock clock;

    /** The latest date allowed on the day this clock says it is. */
    static LocalDate latest(Clock clock) {
      return LocalDate.now(clock.withZone(ZoneOffset.UTC)).plusDays(1).plusYears(1);
    }

    Validator(Clock clock) {
      this.clock = clock;
    }

    @Override
    public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
      if (value == null) {
        return true;
      }
      LocalDate latest = latest(clock);
      if (!value.isBefore(EARLIEST) && !value.isAfter(latest)) {
        return true;
      }
      context.disableDefaultConstraintViolation();
      context
          .buildConstraintViolationWithTemplate(
              "must be between " + EARLIEST + " and " + latest + ", at most one year from today")
          .addConstraintViolation();
      return false;
    }
  }
}
