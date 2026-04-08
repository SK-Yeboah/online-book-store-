package com.bookstore.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bookstore.dto.request.LoginRequest;
import com.bookstore.dto.request.RegisterRequest;
import com.bookstore.dto.response.JwtResponse;
import com.bookstore.exception.InvalidTokenException;
import com.bookstore.exception.handler.ErrorResponse;
import com.bookstore.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Tag(name = "Authentication", description = "Register, login, refresh and logout")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    
    private final UserService userService;



    // Register

    @Operation(
        summary = "Register a new user", 
        description = "Creates a new ROLE_USER account and an empty cart" +"Return 409 if username or email already exists"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "User registered successfully"),
        @ApiResponse(responseCode = "400", description = "Validation failed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Username or email already taken",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirements
    @PostMapping("/register")
    public ResponseEntity<String> register(@Valid @RequestBody RegisterRequest request){
        log.info("Registration attempt for username: {}", request.getUsername());
        String result = userService.register(request);
        log.info("User registered successfully: {}", request.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }


    // Login 
    @Operation(
        summary     = "Login and receive token pair",
        description = "Authenticates credentials. Returns a short-lived access token (15 min) " +
                      "and a long-lived refresh token (7 days). " +
                      "Account locks after 5 consecutive failures."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login successful",
            content = @Content(schema = @Schema(implementation = JwtResponse.class))),
        @ApiResponse(responseCode = "400", description = "Validation failed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Invalid credentials",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "423", description = "Account locked",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirements
    @PostMapping("/login")
    public ResponseEntity<JwtResponse> login(
            @Valid @RequestBody LoginRequest request) {
        log.info("Login attempt for username: {}", request.getUsername());
        JwtResponse response = userService.login(request);
        log.info("Login successful for username: {}", request.getUsername());
        return ResponseEntity.ok(response);
    }

    //  Refresh 
    @Operation(
        summary     = "Refresh access token",
        description = "Exchange a valid refresh token for a new access token + rotated refresh token. " +
                      "The old refresh token is deleted immediately (single-use). " +
                      "If a used refresh token is replayed, ALL sessions are revoked (theft detection)."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tokens refreshed",
            content = @Content(schema = @Schema(implementation = JwtResponse.class))),
        @ApiResponse(responseCode = "401", description = "Refresh token invalid, expired, or reused",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirements
    @PostMapping("/refresh")
    public ResponseEntity<JwtResponse> refresh(
            @Parameter(description = "The refresh token UUID received at login", required = true)
            @RequestParam String refreshToken) {
        log.debug("Token refresh requested");
        JwtResponse response = userService.refresh(refreshToken);
        log.debug("Token refresh successful for user: {}", response.getUsername());
        return ResponseEntity.ok(response);
    }


    // Logout (this device)
    @Operation(
        summary     = "Logout from this device",
        description = "Immediately blacklists the current access token and revokes " +
                      "the associated refresh token. " +
                      "The access token is dead even before its natural expiry."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Logged out successfully"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Parameter(description = "Bearer access token", example = "Bearer eyJ...", required = true)
            @RequestHeader("Authorization") String authHeader,
            @Parameter(description = "Refresh token to revoke", required = true)
            @RequestParam String refreshToken) {
        String accessToken = stripBearer(authHeader);
        userService.logout(accessToken, refreshToken);
        log.info("User logged out (single device)");
        return ResponseEntity.noContent().build();
    }

    // Logout All Devices 
    @Operation(
        summary     = "Logout from ALL devices",
        description = "Blacklists the current access token and revokes EVERY refresh token " +
                      "for this user. All active sessions across all devices are terminated immediately."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Logged out from all devices"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(
            @RequestHeader("Authorization") String authHeader,
            @AuthenticationPrincipal UserDetails userDetails) {
        String accessToken = stripBearer(authHeader);
        userService.logoutAll(accessToken, userDetails.getUsername());
        log.info("User {} logged out from all devices", userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }


    // Private helper
    private String stripBearer(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new InvalidTokenException("Authorization header must start with 'Bearer '");
        }
        return authHeader.substring(7);
    }





    
}
