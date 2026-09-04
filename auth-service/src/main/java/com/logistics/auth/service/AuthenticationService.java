package com.logistics.auth.service;

import com.logistics.auth.dto.request.LoginRequest;
import com.logistics.auth.dto.request.RefreshTokenRequest;
import com.logistics.auth.dto.request.RegisterRequest;
import com.logistics.auth.dto.request.ServiceTokenRequest;
import com.logistics.auth.dto.request.ValidateTokenRequest;
import com.logistics.auth.dto.response.TokenResponse;
import com.logistics.auth.dto.response.TokenValidationResponse;
import com.logistics.auth.dto.response.UserResponse;

/** Use cases of the authentication flow. One method per endpoint of {@code /api/v1/auth}. */
public interface AuthenticationService {

    UserResponse register(RegisterRequest request);

    TokenResponse login(LoginRequest request);

    TokenResponse refresh(RefreshTokenRequest request);

    void logout(RefreshTokenRequest request);

    /** Client-credentials grant for technical accounts (decision D9). */
    TokenResponse issueServiceToken(ServiceTokenRequest request);

    /** Introspection: never throws on an invalid token, reports it instead. */
    TokenValidationResponse validate(ValidateTokenRequest request);
}
