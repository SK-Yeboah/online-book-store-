package com.bookstore.exception;

// // src/main/java/com/bookstore/exception/auth/TokenExpiredException.java
// package com.bookstore.exception.auth;

import org.springframework.http.HttpStatus;
import com.bookstore.exception.BookstoreException;
import com.bookstore.exception.handler.ErrorCode;

public class TokenExpiredException extends BookstoreException {

    public TokenExpiredException(String message) {
        super(ErrorCode.REFRESH_TOKEN_EXPIRED.name(), message, HttpStatus.UNAUTHORIZED);
    }
}