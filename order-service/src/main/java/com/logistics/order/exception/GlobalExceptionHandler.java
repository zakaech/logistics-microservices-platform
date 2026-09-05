package com.logistics.order.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
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
                "Échec de la validation", "Un ou plusieurs champs sont invalides.", request);
        problem.setProperty("errors", violations);
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException exception,
                                          HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ProblemTypes.MALFORMED_REQUEST,
                "Requête malformée", "Le corps de la requête n'a pas pu être analysé.", request);
    }

    /**
     * The shortages travel in the body so a client can act: "we hold 6 of the 10 you asked for" is
     * something a customer can decide about, where a bare "unavailable" is not.
     */
    @ExceptionHandler(AllocationFailedException.class)
    public ProblemDetail handleAllocationFailed(AllocationFailedException exception,
                                                HttpServletRequest request) {
        ProblemDetail problem = build(HttpStatus.CONFLICT, ProblemTypes.ALLOCATION_FAILED,
                "Échec de l'affectation", exception.getMessage(), request);
        problem.setProperty("unsatisfiedLines", exception.getUnsatisfied());
        return problem;
    }

    @ExceptionHandler(ProductUnavailableException.class)
    public ProblemDetail handleProductUnavailable(ProductUnavailableException exception,
                                                  HttpServletRequest request) {
        ProblemDetail problem = build(HttpStatus.UNPROCESSABLE_ENTITY,
                ProblemTypes.PRODUCT_UNAVAILABLE, "Produit indisponible",
                exception.getMessage(), request);
        problem.setProperty("unavailableProducts", exception.getProducts());
        return problem;
    }

    @ExceptionHandler(IllegalOrderStateException.class)
    public ProblemDetail handleIllegalState(IllegalOrderStateException exception,
                                            HttpServletRequest request) {
        ProblemDetail problem = build(HttpStatus.CONFLICT, ProblemTypes.ILLEGAL_ORDER_STATE,
                "État de commande illégal", exception.getMessage(), request);
        problem.setProperty("currentStatus", exception.getCurrentStatus());
        problem.setProperty("allowedTargets", exception.getAllowedTargets());
        return problem;
    }

    /**
     * A downstream outage is not our bug: 503 tells the client that retrying is meaningful, where
     * 500 would suggest the request itself was at fault.
     */
    @ExceptionHandler(UpstreamServiceException.class)
    public ProblemDetail handleUpstream(UpstreamServiceException exception,
                                        HttpServletRequest request) {
        log.warn("Upstream failure calling {}: {}", exception.getService(), exception.getMessage());
        ProblemDetail problem = build(HttpStatus.SERVICE_UNAVAILABLE,
                ProblemTypes.SERVICE_UNAVAILABLE, "Service indisponible",
                "Un service dont dépend cette requête est actuellement indisponible.", request);
        problem.setProperty("service", exception.getService());
        return problem;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException exception,
                                        HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ProblemTypes.RESOURCE_NOT_FOUND,
                "Ressource introuvable", exception.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException exception,
                                            HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, ProblemTypes.ACCESS_DENIED,
                "Accès refusé", "Vos rôles ne permettent pas cette opération.", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException exception,
                                             HttpServletRequest request) {
        log.warn("Database rejected a write on {} {}", request.getMethod(), request.getRequestURI());
        return build(HttpStatus.CONFLICT, ProblemTypes.CONFLICT, "Écriture en conflit",
                "L'opération viole une contrainte d'unicité ou d'intégrité.", request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLocking(OptimisticLockingFailureException exception,
                                                 HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ProblemTypes.CONFLICT, "Modification concurrente",
                "La commande a été modifiée entre-temps. Rechargez-la puis réessayez.", request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException exception,
                                               HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ProblemTypes.MALFORMED_REQUEST,
                "Requête invalide", exception.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(),
                exception);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ProblemTypes.INTERNAL_ERROR,
                "Erreur interne", "Une erreur inattendue s'est produite.", request);
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
