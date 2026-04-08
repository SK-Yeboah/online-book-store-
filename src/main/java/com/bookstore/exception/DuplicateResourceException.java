package com.bookstore.exception;

import org.springframework.http.HttpStatus;

import com.bookstore.exception.handler.ErrorCode;

public class DuplicateResourceException  extends BookstoreException{

    public DuplicateResourceException(String resource, String field, Object value){
        super(
            resolveErrorCode(resource, field).name(),
            resource + " already exists with " + field + ": " + value,
            HttpStatus.CONFLICT);   
    
    }

    private static ErrorCode resolveErrorCode(String resource, String field) {
        if (field.equalsIgnoreCase("username")) return ErrorCode.DUPLICATE_USERNAME;
        if (field.equalsIgnoreCase("email"))    return ErrorCode.DUPLICATE_EMAIL;
        if (field.equalsIgnoreCase("isbn"))     return ErrorCode.DUPLICATE_ISBN;
        return ErrorCode.BAD_REQUEST;
    }
    
}
