package io.kessai.wallet.shared.error;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ProblemDetail> handleDomain(DomainException ex, HttpServletRequest request) {
        log.debug("Domain rule rejected {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage());

        ProblemDetail problem = problemDetail(ex.errorCode(), ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(ex.errorCode().status()).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        List<Map<String, String>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", Objects.toString(error.getDefaultMessage(), "is invalid")))
                .toList();

        ProblemDetail problem = problemDetail(
                ErrorCode.VALIDATION_FAILED, "One or more fields are invalid", path(request));
        problem.setProperty("errors", fieldErrors);

        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status()).body(problem);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);

        // Exception messages leak table names, SQL and file paths. Log them, never return them.
        ProblemDetail problem = problemDetail(
                ErrorCode.INTERNAL_ERROR, "The request could not be completed", request.getRequestURI());
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status()).body(problem);
    }

    private ProblemDetail problemDetail(ErrorCode errorCode, String detail, String path) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(errorCode.status(), detail);
        problem.setType(errorCode.type());
        problem.setTitle(errorCode.title());
        problem.setInstance(URI.create(path));
        problem.setProperty("code", errorCode.name());
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }

    private String path(WebRequest request) {
        String description = request.getDescription(false);   // Spring formats this as "uri=/api/v1/users"
        return description.startsWith("uri=") ? description.substring(4) : description;
    }
}
