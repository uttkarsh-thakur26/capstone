package in.edu.bennett.capstone.knowledge.common;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The servlet container's error path, for failures that never reach a controller, such as a URL
 * that Spring Security's firewall rejects. It replaces Boot's {@code BasicErrorController}, whose
 * JSON is not Problem Details (ADR-023). The original exception is not inspected: by the time the
 * container forwards here, only the status is safe to report.
 */
@Hidden // not an API endpoint; springdoc hides only Boot's own error controller
@RestController
class ProblemErrorController implements ErrorController {

  @RequestMapping("${server.error.path:/error}")
  ResponseEntity<ProblemDetail> error(HttpServletRequest request) {
    HttpStatusCode status = statusOf(request);
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            status,
            status.is5xxServerError()
                ? "An unexpected error occurred."
                : "The request could not be processed.");
    problem.setProperty("code", ProblemCodes.forStatus(status));
    // No "instance": the rejected URL may not even be a valid URI.
    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }

  private static HttpStatusCode statusOf(HttpServletRequest request) {
    Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
    if (code instanceof Integer value && value >= 400 && value <= 599) {
      return HttpStatusCode.valueOf(value);
    }
    return HttpStatus.INTERNAL_SERVER_ERROR;
  }
}
