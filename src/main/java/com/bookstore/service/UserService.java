package com.bookstore.service;

import java.time.Duration;
import java.time.Instant;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.bookstore.dto.request.LoginRequest;
import com.bookstore.dto.request.RegisterRequest;
import com.bookstore.dto.response.JwtResponse;
import com.bookstore.entity.Cart;
import com.bookstore.entity.RefreshToken;
import com.bookstore.entity.User;
import com.bookstore.exception.DuplicateResourceException;
import com.bookstore.exception.ResourceNotFoundException;
import com.bookstore.repository.CartRepository;
import com.bookstore.repository.UserRepository;
import com.bookstore.security.Jwtutil;
import com.bookstore.security.TokenBlacklist;
import com.bookstore.security.UserPrincipal;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final CartRepository cartRepository;
    private final Jwtutil jwtutil;
    private final AccountLockService accountLockService;
    private final TokenBlacklist tokenBlacklist;
    private final RefreshTokenService refreshTokenService;



    //Register
    @Transactional
    public String register(RegisterRequest registerRequest){
        String username = registerRequest.getUsername().trim().toLowerCase();
        String email = registerRequest.getEmail().trim().toLowerCase();

        // Throw Exception for Duplicated Username
        if(userRepository.existsByUsername(username))
            throw new DuplicateResourceException("User", "Username", registerRequest.getUsername());
        
        // Throw Exception  for Duplicated email
        if(userRepository.existsByEmail(email))
            throw new DuplicateResourceException("User", "Email", registerRequest.getEmail());

       
       try{
            User user = new User(username, passwordEncoder.encode(registerRequest.getPassword()), email, User.Role.ROLE_USER);
            User savedUser = userRepository.saveAndFlush(user);
            cartRepository.save(new Cart(savedUser));
            
            log.info("New user registered successfully: {}", username);
            return "User registered successfully";
       }catch(DataIntegrityViolationException ex){
            log.error("Conflict during registration for username: {}", username);
            throw new DuplicateResourceException("User", "Username/Email",email);
       }
        
        

    }


    // Login
    public JwtResponse login(LoginRequest request){
        String username = normalizeUsername(request.getUsername());

        // check the lock Before touching the authentication manager - fail fast
        accountLockService.checkNotLocked(username);

        try{
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, request.getPassword())
            );

            // Cleanup Service
            accountLockService.registerSuccess(username);

            // Reuse principal from authenticate() — includes JPA User (no second lookup)
            UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
            String accessToken = jwtutil.generateAccessToken(principal);
            RefreshToken currentRefreshToken = refreshTokenService.createRefreshToken(principal.getUser());

            log.info("Login Successful (usernameLen={})", username.length());

            return new JwtResponse(
                    accessToken,
                    currentRefreshToken.getToken(),
                    principal.getUsername(),
                    principal.getAuthorities().toString()
            );

        }catch(BadCredentialsException ex){
            accountLockService.registeredFailure(username);
            throw ex;
        }

        
    }


    // Refresh
    public JwtResponse refresh(String rawRefreshToken){
        RefreshToken current = refreshTokenService.validateRefreshToken(rawRefreshToken);
        RefreshToken rotated = refreshTokenService.rotateRefreshToken(current);
        // rotated.getUser() was fully loaded inside rotateRefreshToken's @Transactional,
        // so its scalar fields are in memory. current.getUser() is a detached lazy proxy
        // that throws LazyInitializationException outside its original session.
        String username = rotated.getUser().getUsername();
        UserDetails userDetails = UserPrincipal.from(rotated.getUser());
        String accessToken = jwtutil.generateAccessToken(userDetails);
        log.info("Token rotated successfully for user (usernameLen={})", username.length());
        return new JwtResponse(accessToken, rotated.getToken(), username, rotated.getUser().getRole().name());
    }

    // Logout 
    @Transactional
    public void logout(String accessToken, String rawRefreshToken){
        blacklistAccessToken(accessToken);
        refreshTokenService.revokeToken(rawRefreshToken);
        log.debug("Single-device logout completed");
    }

    // Logout All Devices
    @Transactional
    public void logoutAll(String accessToken, String username){
        blacklistAccessToken(accessToken);
        User user = userRepository.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("User", "Username", username));
        refreshTokenService.revokeAllUserTokens(user.getId());
        log.info("All-device logout for user (usernameLen={})", username.length());
    }

    // Private Helpers

    private static String normalizeUsername(String raw) {
        if (!StringUtils.hasText(raw)) throw new IllegalArgumentException("username must not be blank");
        return raw.trim().toLowerCase();
    }

    private void blacklistAccessToken(String accessToken) {
        String  jti    = jwtutil.extractJti(accessToken);
        Instant expiry = jwtutil.extractExpiry(accessToken);
        Duration ttl   = Duration.between(Instant.now(), expiry);
        tokenBlacklist.blacklist(jti, ttl.isNegative() ? Duration.ZERO : ttl);
    }


    
}
