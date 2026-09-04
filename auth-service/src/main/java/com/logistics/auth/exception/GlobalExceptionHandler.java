package com.logistics.auth.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/**
 * Single place where an exception becomes an HTTP response.
 *
 * <p>Every handler produces an RFC 7807 {@code ProblemDetail}, so a client parses one document shape
 * whatever went wrong. Controllers therefore contain no try/catch and no error mapping at all.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    /** One invalid field of a request body. */
    public record FieldViolation(String field, String message) {
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException exception,
                                          HttpServletRequest request) {
        List<FieldViolation> violations = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();

        ProblemDetail problem = build(HttpStatus.BAD_REQUEST, ProblemTypes.VALIDATION_FAILED,
                "Validation failed", "One or more fields are invalid.", request);
        problem.setProperty("errors", violations);
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException exception,
                                              HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ProblemTypes.MALFORMED_REQUEST,
                "Malformed request", "The request body could not be parsed.", request);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException exception,
                                                  HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ProblemTypes.INVALID_CREDENTIALS,
                "Authentication failed", exception.getMessage(), request);
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ProblemDetail handleInvalidToken(InvalidTokenException exception,
                                            HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ProblemTypes.INVALID_TOKEN,
                "Invalid token", exception.getMessage(), request);
    }

    @ExceptionHandler(EmailAlreadyUsedException.class)
    public ProblemDetail handleEmailAlreadyUsed(EmailAlreadyUsedException exception,
                                                HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ProblemTypes.EMAIL_ALREADY_USED,
                "Email already used", exception.getMessage(), request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException exception,
                                        HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ProblemTypes.RESOURCE_NOT_FOUND,
                "Resource not found", exception.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException exception,
                                            HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, ProblemTypes.ACCESS_DENIED,
                "Access denied", "Your roles do not allow this operation.", request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException exception,
                                               HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ProblemTypes.MALFORMED_REQUEST,
                "Invalid request", exception.getMessage(), request);
    }

    /**
     * Last resort. The cause is logged in full but never returned: a stack trace in a response body
     * hands an attacker a map of the internals.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(),
                exception);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ProblemTypes.INTERNAL_ERROR,
                "Internal error", "An unexpected error occurred.", request);
    }

    private ProblemDetail build(HttpStatus status, URI type, String title, String detail,
                                HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(type);
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());

        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId != null) {
            problem.setProperty("requestId", requestId);
        }
        return problem;
    }
}
