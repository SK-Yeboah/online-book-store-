package com.bookstore.exception.handler;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;
import java.util.List;

// ─────────────────────────────────────────────────────────────────────
// ErrorResponse — the ONLY error shape the API ever returns.
//
// Every error, whether validation failure, not found, locked account,
// or server crash, produces this exact structure. Clients never have
// to guess what shape an error response will be.
//
// Example output:
// {
//   "timestamp":  "2024-01-15T10:30:00",
//   "status":     404,
//   "errorCode":  "BOOK_NOT_FOUND",
//   "message":    "Book not found with id: 99",
//   "path":       "/api/books/99"
// }
//
// For validation errors, fieldErrors is also populated:
// {
//   "timestamp":  "2024-01-15T10:30:00",
//   "status":     400,
//   "errorCode":  "VALIDATION_FAILED",
//   "message":    "Request validation failed",
//   "path":       "/api/auth/register",
//   "fieldErrors": [
//     { "field": "email",    "message": "Must be a valid email" },
//     { "field": "password", "message": "At least 6 characters" }
//   ]
// }
// ─────────────────────────────────────────────────────────────────────
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL) // omit null fields (e.g. fieldErrors if not a validation error)
public class ErrorResponse {

    private final LocalDateTime     timestamp;
    private final int               status;
    private final ErrorCode         errorCode;
    private final String            message;
    private final String            path;
    private final List<FieldError>  fieldErrors; // only populated for validation errors

    // ── Field-level validation error 
    @Getter
    @Builder
    public static class FieldError {
        private final String field;
        private final String rejectedValue; // what the client sent
        private final String message;       // what was wrong with it
    }

    // ── Factory method 
    public static ErrorResponse of(int status, ErrorCode errorCode,
                                    String message, String path) {
        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status)
                .errorCode(errorCode)
                .message(message)
                .path(path)
                .build();
    }
}