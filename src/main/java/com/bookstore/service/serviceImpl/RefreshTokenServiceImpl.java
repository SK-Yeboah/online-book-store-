package com.bookstore.service.serviceImpl;

import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.bookstore.entity.RefreshToken;
import com.bookstore.entity.User;
import com.bookstore.exception.InvalidTokenException;
import com.bookstore.exception.TokenExpiredException;
import com.bookstore.repository.RefreshTokenRepository;
import com.bookstore.service.RefreshTokenService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import java.util.Objects;


@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-token.expiration:604800000}")
    private Long refreshTokenExpiration;



    @Transactional
    @Override
    public RefreshToken createRefreshToken(User user) {
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(user.getId(), "user.id");

        RefreshToken token = new RefreshToken(
                UUID.randomUUID().toString(),
                user,
                Instant.now().plusMillis(refreshTokenExpiration));

        return refreshTokenRepository.save(token);
    }

    @Override
    public RefreshToken validateRefreshToken(String token){

        RefreshToken refreshToken = refreshTokenRepository.findByToken(token).orElseThrow(() -> new InvalidTokenException("refresh token not found or already revokked"));
        
        if(refreshToken.isUsed()){
            revokeAllUserTokens(refreshToken.getUser().getId());
            throw new InvalidTokenException("Refresh token reuse detected. All sessions have been revoked for security");
        }

        // Expiry check
        if(refreshToken.isExpired()){
            refreshTokenRepository.deleteByToken(token);
            throw new TokenExpiredException("Refresh Token");
        }

        return refreshToken;
    }


    // Rotate Token — marks old token used=true (kept for theft detection), issues a new one.
    // Re-fetches the token within this transaction to avoid LazyInitializationException
    // when the entity was loaded in a different (now-closed) Hibernate session.
    @Transactional
    public RefreshToken rotateRefreshToken(RefreshToken oldToken){
        RefreshToken managed = refreshTokenRepository.findByToken(oldToken.getToken())
                .orElseThrow(() -> new InvalidTokenException("Token not found during rotation"));
        managed.setUsed(true);
        refreshTokenRepository.save(managed);
        return createRefreshToken(managed.getUser());
    }

    // Revoke single token
    @Transactional
    public void revokeToken(String token){
        refreshTokenRepository.deleteByToken(token);
    }

    // Revoke all Tokens for a user
    @Transactional
    public void revokeAllUserTokens(Long userId){
        refreshTokenRepository.deleteByUserById(userId);
    }

    // Scheduled cleanup
    @Scheduled(fixedRate = 3_600_000)
    @Transactional
    public void cleanupExpiredTokens(){
        refreshTokenRepository.deleteAllExpired(Instant.now());
    }



    
}
