package com.logistics.inventory.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
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

/** Single place where an exception becomes an HTTP response, in RFC 7807 form. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";

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
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException exception,
                                          HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ProblemTypes.MALFORMED_REQUEST,
                "Malformed request", "The request body could not be parsed.", request);
    }

    /**
     * The shortages are returned in the body so order-service can re-plan: it re-reads availability
     * and retries the allocation once, which it can only do knowing what was short and by how much.
     */
    @ExceptionHandler(InsufficientStockException.class)
    public ProblemDetail handleInsufficientStock(InsufficientStockException exception,
                                                 HttpServletRequest request) {
        ProblemDetail problem = build(HttpStatus.CONFLICT, ProblemTypes.INSUFFICIENT_STOCK,
                "Insufficient stock", exception.getMessage(), request);
        problem.setProperty("shortages", exception.getShortages());
        return problem;
    }

    @ExceptionHandler(InvalidReservationStateException.class)
    public ProblemDetail handleInvalidReservationState(InvalidReservationStateException exception,
                                                       HttpServletRequest request) {
        ProblemDetail problem = build(HttpStatus.CONFLICT, ProblemTypes.INVALID_RESERVATION_STATE,
                "Invalid reservation state", exception.getMessage(), request);
        problem.setProperty("currentStatus", exception.getCurrentStatus());
        return problem;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException exception,
                                        HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ProblemTypes.RESOURCE_NOT_FOUND,
                "Resource not found", exception.getMessage(), request);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ProblemDetail handleDuplicate(DuplicateResourceException exception,
                                         HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ProblemTypes.DUPLICATE_RESOURCE,
                "Duplicate resource", exception.getMessage(), request);
    }

    /**
     * The database refused the write. In practice this is the check constraint on
     * quantity_reserved &lt;= quantity_on_hand doing its job, which means application code tried
     * something it should have prevented - worth logging loudly.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException exception,
                                             HttpServletRequest request) {
        log.warn("Database rejected a write on {} {}: {}", request.getMethod(),
                request.getRequestURI(), exception.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, ProblemTypes.CONFLICT, "Conflicting write",
                "The operation violates a stock or uniqueness constraint.", request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLocking(OptimisticLockingFailureException exception,
                                                 HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ProblemTypes.CONFLICT, "Concurrent modification",
                "The record was modified concurrently. Reload it and retry.", request);
    }

    /**
     * A row lock could not be taken in time. Mapped to 409 rather than 500: nothing is broken, the
     * caller simply lost a race and retrying is the right response.
     */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ProblemDetail handlePessimisticLocking(PessimisticLockingFailureException exception,
                                                  HttpServletRequest request) {
        log.warn("Lock acquisition failed on {} {}", request.getMethod(), request.getRequestURI());
        return build(HttpStatus.CONFLICT, ProblemTypes.CONFLICT, "Stock temporarily locked",
                "The stock rows are being updated by another operation. Retry shortly.", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException exception,
                                            HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, ProblemTypes.ACCESS_DENIED,
                "Access denied", "Your roles do not allow this operation.", request);
    }

    /**
     * Domain guards from StockItem and Reservation. These are refusals to break an invariant, not
     * malformed input, which is why they are 409 and not 400.
     */
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ProblemDetail handleDomainRule(RuntimeException exception, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ProblemTypes.CONFLICT,
                "Operation refused", exception.getMessage(), request);
    }

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
