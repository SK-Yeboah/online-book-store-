package com.bookstore.service;

import com.bookstore.exception.AccountLockedException;
import com.bookstore.exception.InvalidCredentialsException;

public interface AccountLockService {

    void checkNotLocked(String username) throws AccountLockedException;
    void registeredFailure(String username) throws InvalidCredentialsException;
    void registerSuccess(String username);
    
}
