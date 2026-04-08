package com.bookstore.service;

import com.bookstore.entity.LoginAttempt;
import com.bookstore.exception.AccountLockedException;
import com.bookstore.exception.InvalidCredentialsException;
import com.bookstore.repository.LoginAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("AccountLockService")
class AccountLockServiceTest {

    @Autowired AccountLockService accountLockService;
    @Autowired LoginAttemptRepository loginAttemptRepository;

    private static final String TEST_USER = "concurrentuser";

    @BeforeEach
    void cleanUp() {
        loginAttemptRepository.findByUsername(TEST_USER)
                .ifPresent(loginAttemptRepository::delete);
    }

    @Test
    @DisplayName("concurrent failures — account locks exactly at maxAttempts")
    void concurrentFailures_lockExactlyAtMaxAttempts() throws InterruptedException {
        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    latch.await(); // all threads start simultaneously
                    accountLockService.registeredFailure(TEST_USER);
                } catch (AccountLockedException | InvalidCredentialsException | InterruptedException ignored) {}
            });
        }

        latch.countDown(); // release all threads at once
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        // Despite 10 concurrent attempts, failedAttempts must be exactly 5
        LoginAttempt result = loginAttemptRepository.findByUsername(TEST_USER).orElseThrow();
        assertThat(result.getFailedAttempts()).isEqualTo(5);
        assertThat(result.getLockedUntil()).isNotNull();
    }

    @Test
    @DisplayName("sequential failures — account locks after exactly maxAttempts")
    void sequentialFailures_locksAtMaxAttempts() {
        // Attempts 1-4 should throw InvalidCredentialsException
        for (int i = 1; i < 5; i++) {
            assertThatThrownBy(() -> accountLockService.registeredFailure(TEST_USER))
                    .isInstanceOf(InvalidCredentialsException.class);
        }
        // Attempt 5 should lock the account
        assertThatThrownBy(() -> accountLockService.registeredFailure(TEST_USER))
                .isInstanceOf(AccountLockedException.class);

        // Subsequent calls should also throw AccountLockedException via checkNotLocked
        assertThatThrownBy(() -> accountLockService.checkNotLocked(TEST_USER))
                .isInstanceOf(AccountLockedException.class);
    }

    @Test
    @DisplayName("successful login resets the failure counter")
    void registerSuccess_resetsCounter() {
        // Build up 3 failures
        for (int i = 0; i < 3; i++) {
            try { accountLockService.registeredFailure(TEST_USER); } catch (Exception ignored) {}
        }

        accountLockService.registerSuccess(TEST_USER);

        LoginAttempt result = loginAttemptRepository.findByUsername(TEST_USER).orElseThrow();
        assertThat(result.getFailedAttempts()).isZero();
        assertThat(result.getLockedUntil()).isNull();
    }
}
