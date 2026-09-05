package com.logistics.auth.controller;

import com.logistics.auth.dto.request.LoginRequest;
import com.logistics.auth.dto.request.RefreshTokenRequest;
import com.logistics.auth.dto.request.RegisterRequest;
import com.logistics.auth.dto.request.ServiceTokenRequest;
import com.logistics.auth.dto.request.ValidateTokenRequest;
import com.logistics.auth.dto.response.TokenResponse;
import com.logistics.auth.dto.response.TokenValidationResponse;
import com.logistics.auth.dto.response.UserResponse;
import com.logistics.auth.service.AuthenticationService;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * Public authentication endpoints.
 *
 * <p>The controller binds, delegates and maps - it holds no business rule and no try/catch. Errors
 * become responses in {@code GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Inscription, connexion, émission et validation des jetons")
public class AuthController {

    private final AuthenticationService authenticationService;
    private final RSAKey rsaSigningKey;

    @PostMapping("/register")
    @Operation(summary = "Créer un compte ; le rôle ROLE_CLIENT est toujours imposé par le serveur")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse created = authenticationService.register(request);
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/users/{id}")
                        .buildAndExpand(created.id())
                        .toUri())
                .body(created);
    }

    @PostMapping("/login")
    @Operation(summary = "Échanger des identifiants contre un jeton d'accès et un jeton de rafraîchissement")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authenticationService.login(request);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Faire tourner le jeton de rafraîchissement et obtenir un nouveau jeton d'accès")
    public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authenticationService.refresh(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Révoquer un jeton de rafraîchissement")
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        authenticationService.logout(request);
    }

    @PostMapping("/token")
    @Operation(summary = "Authentification par identifiants client pour les comptes techniques (ROLE_SERVICE)")
    public TokenResponse serviceToken(@Valid @RequestBody ServiceTokenRequest request) {
        return authenticationService.issueServiceToken(request);
    }

    /**
     * Token introspection.
     *
     * <p>Services validate tokens locally against the JWKS - that is the fast path and it needs no
     * network call. This endpoint exists for the cases local validation cannot serve: a client
     * checking why a token is refused, an operator diagnosing a clock or issuer mismatch, and any
     * component that cannot embed a JOSE library.
     */
    @PostMapping("/validate")
    @Operation(summary = "Inspecter un jeton ; renvoie 200 avec valid=false plutôt qu'une 401")
    public TokenValidationResponse validate(@Valid @RequestBody ValidateTokenRequest request) {
        return authenticationService.validate(request);
    }

    /**
     * Public half of the signing key, in JWKS format.
     *
     * <p>This is what makes RS256 operationally practical: every other service discovers the public
     * key here instead of having it copied into its own configuration, and a key rotation is picked
     * up by looking at the {@code kid} in the token header.
     */
    @GetMapping("/.well-known/jwks.json")
    @Operation(summary = "Clés publiques permettant de vérifier la signature des jetons")
    public Map<String, Object> jwks() {
        return new JWKSet(rsaSigningKey.toPublicJWK()).toJSONObject();
    }
}
