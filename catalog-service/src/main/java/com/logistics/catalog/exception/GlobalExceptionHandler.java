package com.logistics.catalog.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
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
     * Attribute problems are 422, not 400: the JSON is syntactically fine and every field is
     * individually valid. It is the combination with the category attribute schema that is not.
     */
    @ExceptionHandler(InvalidProductAttributesException.class)
    public ProblemDetail handleInvalidAttributes(InvalidProductAttributesException exception,
                                                 HttpServletRequest request) {
        ProblemDetail problem = build(HttpStatus.UNPROCESSABLE_ENTITY,
                ProblemTypes.INVALID_ATTRIBUTES, "Attributs produit invalides",
                exception.getMessage(), request);
        problem.setProperty("attributeErrors", exception.getViolations());
        return problem;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException exception,
                                        HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ProblemTypes.RESOURCE_NOT_FOUND,
                "Ressource introuvable", exception.getMessage(), request);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ProblemDetail handleDuplicate(DuplicateResourceException exception,
                                         HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ProblemTypes.DUPLICATE_RESOURCE,
                "Ressource en double", exception.getMessage(), request);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ProblemDetail handleDuplicateKey(DuplicateKeyException exception,
                                            HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ProblemTypes.DUPLICATE_RESOURCE,
                "Ressource en double", "Un document portant la même clé unique existe déjà.", request);
    }

    @ExceptionHandler(CategoryNotEmptyException.class)
    public ProblemDetail handleCategoryNotEmpty(CategoryNotEmptyException exception,
                                                HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ProblemTypes.CATEGORY_NOT_EMPTY,
                "Catégorie non vide", exception.getMessage(), request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLocking(OptimisticLockingFailureException exception,
                                                 HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ProblemTypes.CONFLICT, "Modification concurrente",
                "Le document a été modifié entre-temps. Rechargez-le puis réessayez.", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException exception,
                                            HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, ProblemTypes.ACCESS_DENIED,
                "Accès refusé", "Vos rôles ne permettent pas cette opération.", request);
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
