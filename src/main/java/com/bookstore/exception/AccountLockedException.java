package com.bookstore.exception;

import lombok.Getter;

@Getter
public class AccountLockedException  extends RuntimeException{

    private final long minutesRemaining;

  
    public AccountLockedException (Long minutesRemaining){
        super("Account is temporily locked");
        this.minutesRemaining = minutesRemaining;
    }

    
}
