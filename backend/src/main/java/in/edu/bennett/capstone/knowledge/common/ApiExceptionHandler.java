package in.edu.bennett.capstone.knowledge.common;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
    return handleExceptionInternal(ex, problem, new HttpHeaders(), ex.status(), request);
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
