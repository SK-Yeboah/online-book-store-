package com.bookstore.exception;

// // src/main/java/com/bookstore/exception/auth/InvalidTokenException.java
// package com.bookstore.exception.auth;

import org.springframework.http.HttpStatus;
import com.bookstore.exception.BookstoreException;
import com.bookstore.exception.handler.ErrorCode;

public class InvalidTokenException extends BookstoreException {

    public InvalidTokenException(String message) {
        super(ErrorCode.TOKEN_INVALID.name(), message, HttpStatus.UNAUTHORIZED);
    }

    public InvalidTokenException(ErrorCode code, String message) {
        super(code.name(), message, HttpStatus.UNAUTHORIZED);
    }
}