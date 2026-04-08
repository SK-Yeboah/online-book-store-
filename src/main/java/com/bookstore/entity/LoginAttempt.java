package com.bookstore.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "login_attempts", 
       indexes = {@Index(name = "idx_username", columnList = "username")})
@Data
@NoArgsConstructor
public class LoginAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String username;

    //Incremented on each failed login, reset on Succcess
    @Column(nullable = false)
    private int failedAttempts = 0;

    // Set when failedAttempts >= max - null means not locked
    @Column
    private LocalDateTime lockedUntil;

    // Timestapm of last failed attempt - used to auto-reset count
    // after a long period of inactivity (e.g 1 hour)
    @Column
    private LocalDateTime lastFailedAt;

    public LoginAttempt(String username) {
        this.username = username;
    }

    public boolean isCurrentlyLocked(){
        return lockedUntil != null && LocalDateTime.now().isBefore(lockedUntil);
    }
}
