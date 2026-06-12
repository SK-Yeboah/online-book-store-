package com.bookstore.exception;

// // src/main/java/com/bookstore/exception/resource/ResourceNotFoundException.java
// package com.bookstore.exception.resource;

import org.springframework.http.HttpStatus;
import com.bookstore.exception.handler.ErrorCode;

public class ResourceNotFoundException extends BookstoreException {

    public ResourceNotFoundException(String resource, String field, Object value) {
        super(resolve(resource).name(),
                resource + " not found for " + field + ": " + value,
                HttpStatus.NOT_FOUND);
    }

    private static ErrorCode resolve(String resource) {
        if (resource == null) return ErrorCode.BAD_REQUEST;
        return switch (resource.toLowerCase()) {
            case "user"  -> ErrorCode.USER_NOT_FOUND;
            case "book"  -> ErrorCode.BOOK_NOT_FOUND;
            case "cart"  -> ErrorCode.CART_NOT_FOUND;
            case "cartitem", "cart item", "cart_item" -> ErrorCode.CART_ITEM_NOT_FOUND;
            case "order" -> ErrorCode.ORDER_NOT_FOUND;
            default      -> ErrorCode.BAD_REQUEST;
        };
    }
}