package in.edu.bennett.capstone.knowledge.common;

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
 * Text a person typed: no control characters except tab, line feed and carriage return. Checked at
 * the boundary (15.1) because PostgreSQL cannot store or compare a NUL character in text, so such
 * input would otherwise fail in the database as a 500. Null is valid; combine with {@code NotBlank}
 * where a value is required.
 */
@Documented
@Constraint(validatedBy = PlainText.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface PlainText {

  String message() default "must not contain control characters";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  class Validator implements ConstraintValidator<PlainText, CharSequence> {

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
      return value == null
          || value
              .codePoints()
              .noneMatch(c -> Character.isISOControl(c) && c != '\t' && c != '\n' && c != '\r');
    }
  }
}
