package com.bookstore.exception;

import org.springframework.http.HttpStatus;
import lombok.Getter;

@Getter
public class BookstoreException extends RuntimeException {
    private final String errorCode;
    private final HttpStatus status;

    public BookstoreException(String errorCode, String message, HttpStatus status) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }
}