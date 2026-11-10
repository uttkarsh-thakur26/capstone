package in.edu.bennett.capstone.knowledge.common;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;

/**
 * The one place where exceptions become Problem Details (specification 15.1, 24.4, ADR-023).
 *
 * <p>Spring MVC's own exceptions keep the standard responses of {@link
 * ResponseEntityExceptionHandler}; every response then gets a {@code code}. The security filter
 * chain hands its {@code 401} and {@code 403} to this class too, so they are mapped here as well.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  /** The same code as the upload's own size check, so the client sees one code either way. */
  private static final String FILE_TOO_LARGE = "FILE_TOO_LARGE";

  @ExceptionHandler(ApiException.class)
  ResponseEntity<Object> handleApiException(ApiException ex, WebRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.status(), ex.getMessage());
    ex.properties().forEach(problem::setProperty);
    problem.setProperty("code", ex.code());
    HttpHeaders headers = new HttpHeaders();
    // A 429 also says when to come back in the standard header (RFC 9110, SEC-8).
    if (ex.properties().get(RateLimitedException.RETRY_AFTER_SECONDS) instanceof Long seconds) {
      headers.set(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
    }
    return handleExceptionInternal(ex, problem, headers, ex.status(), request);
  }

  /** A multipart request above the configured limit (DOC-1), stopped before the controller runs. */
  @Override
  protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
      MaxUploadSizeExceededException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(status, "The file is larger than the upload limit.");
    problem.setProperty("code", FILE_TOO_LARGE);
    return handleExceptionInternal(ex, problem, headers, status, request);
  }

  @ExceptionHandler(AuthenticationException.class)
  ResponseEntity<Object> handleAuthentication(AuthenticationException ex, WebRequest request) {
    return problem(
        ex,
        HttpStatus.UNAUTHORIZED,
        "Authentication is required to access this resource.",
        request);
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
    return problem(ex, HttpStatus.FORBIDDEN, "You are not allowed to do this.", request);
  }

  /**
   * Two requests changed the same row at the same time, and the row version let only the first
   * succeed (DATA-3). The client reloads and tries again.
   */
  @ExceptionHandler(OptimisticLockingFailureException.class)
  ResponseEntity<Object> handleConcurrentChange(
      OptimisticLockingFailureException ex, WebRequest request) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            "It was changed by someone else at the same time. Reload it and try again.");
    problem.setProperty("code", "CONCURRENT_CHANGE");
    return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.CONFLICT, request);
  }

  /** Anything not mapped above is a bug: log it, and tell the client nothing about it. */
  @ExceptionHandler(Exception.class)
  ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
    log.error("Unhandled exception for {}", request.getDescription(false), ex);
    return problem(ex, HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.", request);
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<FieldErrorResponse> errors = new ArrayList<>();
    for (FieldError error : ex.getBindingResult().getFieldErrors()) {
      errors.add(new FieldErrorResponse(error.getField(), error.getDefaultMessage()));
    }
    ex.getBindingResult()
        .getGlobalErrors()
        .forEach(
            error -> errors.add(new FieldErrorResponse(error.getObjectName(), messageOf(error))));
    return validationProblem(ex, errors, headers, status, request);
  }

  @Override
  protected ResponseEntity<Object> handleHandlerMethodValidationException(
      HandlerMethodValidationException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<FieldErrorResponse> errors = new ArrayList<>();
    ex.getParameterValidationResults()
        .forEach(
            result ->
                result
                    .getResolvableErrors()
                    .forEach(
                        error ->
                            errors.add(
                                new FieldErrorResponse(
                                    result.getMethodParameter().getParameterName(),
                                    messageOf(error)))));
    return validationProblem(ex, errors, headers, status, request);
  }

  /**
   * A body whose JSON is well formed but holds a value of the wrong kind for a field, such as a
   * date that does not exist ("2026-02-30") or a category that is not in the list, answers like a
   * failed validation, naming the field, so a client that skipped the form learns what to fix
   * (15.1, ADR-040). Anything else unreadable keeps Spring's standard answer.
   */
  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    MismatchedInputException mismatch = mismatchIn(ex);
    String field = mismatch == null ? "" : fieldOf(mismatch);
    if (field.isEmpty()) {
      return super.handleHttpMessageNotReadable(ex, headers, status, request);
    }
    return validationProblem(
        ex,
        List.of(new FieldErrorResponse(field, expected(mismatch.getTargetType()))),
        headers,
        status,
        request);
  }

  /** A query parameter of the wrong kind, such as {@code ?category=SPORTS}, names the parameter. */
  @Override
  protected ResponseEntity<Object> handleTypeMismatch(
      TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    String name =
        ex instanceof MethodArgumentTypeMismatchException argument
            ? argument.getName()
            : ex.getPropertyName();
    if (name == null) {
      return super.handleTypeMismatch(ex, headers, status, request);
    }
    return validationProblem(
        ex,
        List.of(new FieldErrorResponse(name, expected(ex.getRequiredType()))),
        headers,
        status,
        request);
  }

  private static MismatchedInputException mismatchIn(Throwable ex) {
    for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
      if (cause instanceof MismatchedInputException mismatch) {
        return mismatch;
      }
    }
    return null;
  }

  /** The field's path, such as {@code documentDate} or {@code grants[0].type}. */
  private static String fieldOf(JacksonException ex) {
    StringBuilder field = new StringBuilder();
    for (JacksonException.Reference reference : ex.getPath()) {
      if (reference.getPropertyName() != null) {
        if (!field.isEmpty()) {
          field.append('.');
        }
        field.append(reference.getPropertyName());
      } else if (reference.getIndex() >= 0) {
        field.append('[').append(reference.getIndex()).append(']');
      }
    }
    return field.toString();
  }

  /** What the field takes, in words. Never the rejected value. */
  private static String expected(Class<?> type) {
    if (type == null) {
      return "has a value of the wrong kind";
    }
    if (type == LocalDate.class) {
      return "must be a real date, written as YYYY-MM-DD";
    }
    if (type.isEnum()) {
      return "must be one of "
          + Arrays.stream(type.getEnumConstants())
              .map(constant -> ((Enum<?>) constant).name())
              .collect(Collectors.joining(", "));
    }
    if (type == UUID.class) {
      return "must be an id";
    }
    return "has a value of the wrong kind";
  }

  @Override
  protected ResponseEntity<Object> handleNoResourceFoundException(
      NoResourceFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    // Spring's own detail names a "static resource", which describes the server, not the request.
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(status, "The requested resource does not exist.");
    return handleExceptionInternal(ex, problem, headers, status, request);
  }

  /** The last step for every response of this class: each problem gets its {@code code}. */
  @Override
  protected ResponseEntity<Object> createResponseEntity(
      Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
    if (body instanceof ProblemDetail problem
        && (problem.getProperties() == null || !problem.getProperties().containsKey("code"))) {
      problem.setProperty("code", ProblemCodes.forStatus(statusCode));
    }
    return super.createResponseEntity(body, headers, statusCode, request);
  }

  private ResponseEntity<Object> problem(
      Exception ex, HttpStatus status, String detail, WebRequest request) {
    return handleExceptionInternal(
        ex, ProblemDetail.forStatusAndDetail(status, detail), new HttpHeaders(), status, request);
  }

  private ResponseEntity<Object> validationProblem(
      Exception ex,
      List<FieldErrorResponse> errors,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "The request is not valid.");
    problem.setProperty("code", ProblemCodes.VALIDATION_FAILED);
    problem.setProperty("errors", errors);
    return handleExceptionInternal(ex, problem, headers, status, request);
  }

  private static String messageOf(MessageSourceResolvable error) {
    return error.getDefaultMessage() == null ? "is not valid" : error.getDefaultMessage();
  }
}
