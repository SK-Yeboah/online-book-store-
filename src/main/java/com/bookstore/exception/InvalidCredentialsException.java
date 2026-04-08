package com.bookstore.exception;

import lombok.Getter;

@Getter
public class InvalidCredentialsException extends RuntimeException{
    private final int remainingAttempts;

    
    public InvalidCredentialsException (int remainingAttempts){
        super("Invalid username or password");
        this.remainingAttempts = remainingAttempts;
    }
    
}
