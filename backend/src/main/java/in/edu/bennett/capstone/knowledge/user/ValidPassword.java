package in.edu.bennett.capstone.knowledge.user;

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
 * AUTH-4: 10 to 128 characters, no composition rules. Characters are Unicode code points, not Java
 * {@code char}s: {@code @Size} would count an emoji as two and accept five of them as ten.
 */
@Documented
@Constraint(validatedBy = ValidPassword.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {

  int MIN_LENGTH = 10;
  int MAX_LENGTH = 128;

  String message() default "must be between 10 and 128 characters long";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  class Validator implements ConstraintValidator<ValidPassword, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
      return isValidLength(value);
    }

    /** Also used where no validator runs, such as the bootstrap admin's configured password. */
    public static boolean isValidLength(String password) {
      if (password == null) {
        return false;
      }
      int length = password.codePointCount(0, password.length());
      return length >= MIN_LENGTH && length <= MAX_LENGTH;
    }
  }
}
