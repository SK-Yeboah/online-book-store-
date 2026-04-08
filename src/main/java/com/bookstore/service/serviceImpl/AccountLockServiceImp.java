package com.bookstore.service.serviceImpl;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import com.bookstore.entity.LoginAttempt;
import com.bookstore.repository.LoginAttemptRepository;
import com.bookstore.service.AccountLockService;
import org.springframework.util.StringUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import com.bookstore.exception.AccountLockedException;
import com.bookstore.exception.InvalidCredentialsException;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountLockServiceImp implements AccountLockService {

    private final LoginAttemptRepository loginAttemptRepository;

    @Value("${security.lockout.max-attempts:5}")
    private int maxAttempts;

    @Value("${security.lockout.duration-minutes:15}")
    private int lockDurationMinutes;

    @Value("${security.lockout.reset-after-hours:24}")
    private int resetAfterHours;


    @PostConstruct
    void validateTokenSettings(){
        if(maxAttempts < 1){
            throw new IllegalStateException("security.lockout.max-attempts  must be >= 1");
        }

        if(lockDurationMinutes < 1){
            throw new IllegalStateException("security.lockout.duration-minutes must be >= 1");
        }

        if(resetAfterHours < 1){
            throw new IllegalStateException("security.lockout.reset-after-hours must be >= 1");
        }
    }
    
    

    @Override
    @Transactional(readOnly = true)
    public void checkNotLocked(String username) {
        String normalized = normalizedUsername(username);
        loginAttemptRepository.findByUsername(normalized).ifPresent(attempt -> {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime lockedUntil = attempt.getLockedUntil();
            if(lockedUntil != null && lockedUntil.isAfter(now)){
                long minutesLeft = Math.max(1, ChronoUnit.MINUTES.between(now, lockedUntil));
                throw new AccountLockedException(minutesLeft);
            }
        });
    }

    @Override
    @Transactional(noRollbackFor = {InvalidCredentialsException.class, AccountLockedException.class})
    public void registeredFailure(String username){
        String normalized = normalizedUsername(username);
        LoginAttempt attempt = loadOrCreateLocked(normalized);

        LocalDateTime now = LocalDateTime.now();
        resetStaleFailureWindowIfNeeded(attempt, now);

        int failures = attempt.getFailedAttempts() + 1;
        attempt.setFailedAttempts(failures);
        attempt.setLastFailedAt(now);

        if(failures >= maxAttempts){
            attempt.setLockedUntil(now.plusMinutes(lockDurationMinutes));
            loginAttemptRepository.save(attempt);
            log.warn("Account locked after {} failed attempt (username={})", maxAttempts, normalized.length());
            throw new AccountLockedException((long)lockDurationMinutes);
        }

        loginAttemptRepository.save(attempt);
        int remaining = maxAttempts - failures;
        throw new InvalidCredentialsException(Math.max(0, remaining));
    }

    @Override
    @Transactional
    public void registerSuccess(String username){
        String normalized = normalizedUsername(username);
        loginAttemptRepository.lockByUsername(normalized).ifPresent(attempt ->{
            attempt.setFailedAttempts(0);
            attempt.setLockedUntil(null);
            attempt.setLastFailedAt(null);
            loginAttemptRepository.save(attempt);
            log.debug("cleared login-attempt counters after successful authentication");
        });
    }






    private void resetStaleFailureWindowIfNeeded(LoginAttempt attempt, LocalDateTime now) {
        LocalDateTime lastFailed = attempt.getLastFailedAt();

        if(lastFailed != null && lastFailed.isBefore(now.minusHours(resetAfterHours))){
            attempt.setFailedAttempts(0);
        }

    }



    private LoginAttempt loadOrCreateLocked(String username) {
        return loginAttemptRepository.lockByUsername(username).orElseGet(() -> {
            try {
                return loginAttemptRepository.saveAndFlush(new LoginAttempt(username));
            }catch(DataIntegrityViolationException ex){
                return loginAttemptRepository.lockByUsername(username).orElseThrow(() -> new IllegalStateException(
                    "LoginAttempt missing after unique-key conflict for username", ex
                ));
            }
        });
    }



    private String normalizedUsername(String username) {

        if(!StringUtils.hasText(username)){
            throw new IllegalArgumentException("username must not be blank");
        }
       
        return username.trim().toLowerCase();
    }



    
}
