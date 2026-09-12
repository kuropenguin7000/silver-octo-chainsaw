package io.kessai.wallet.shared.error;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.databind.exc.InvalidFormatException;

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

    /**
     * Overridden rather than added as another {@code @ExceptionHandler}: the superclass already
     * maps this type, and two handlers for one exception fail the context at startup.
     *
     * <p>A rejected enum (an unsupported currency) is reported in the same {@code errors} shape as
     * Bean Validation failures, so clients have one thing to parse rather than two.
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        if (ex.getCause() instanceof InvalidFormatException cause
                && cause.getTargetType() != null
                && cause.getTargetType().isEnum()) {

            String allowed = Arrays.stream(cause.getTargetType().getEnumConstants())
                    .map(String::valueOf)
                    .collect(Collectors.joining(", "));

            ProblemDetail problem = problemDetail(
                    ErrorCode.VALIDATION_FAILED, "One or more fields are invalid", path(request));
            problem.setProperty("errors", List.of(Map.of(
                    "field", fieldName(cause),
                    "message", "must be one of [" + allowed + "]")));

            return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status()).body(problem);
        }

        ProblemDetail problem = problemDetail(
                ErrorCode.MALFORMED_REQUEST, "Request body could not be parsed", path(request));
        return ResponseEntity.status(ErrorCode.MALFORMED_REQUEST.status()).body(problem);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        Class<?> required = ex.getRequiredType();
        String detail = "Parameter '" + ex.getName() + "' must be a valid "
                + (required == null ? "value" : required.getSimpleName());

        // Deliberately does not echo ex.getValue() -- never reflect unvalidated input back.
        ProblemDetail problem =
                problemDetail(ErrorCode.MALFORMED_REQUEST, detail, request.getRequestURI());
        return ResponseEntity.status(ErrorCode.MALFORMED_REQUEST.status()).body(problem);
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

    private String fieldName(InvalidFormatException cause) {
        return cause.getPath().isEmpty()
                ? "body"
                : cause.getPath().getLast().getPropertyName();
    }

    private String path(WebRequest request) {
        String description = request.getDescription(false);   // Spring formats this as "uri=/api/v1/users"
        return description.startsWith("uri=") ? description.substring(4) : description;
    }
}
