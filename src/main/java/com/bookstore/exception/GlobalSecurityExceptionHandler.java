package com.bookstore.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.bookstore.exception.handler.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@ControllerAdvice
@Slf4j
public class GlobalSecurityExceptionHandler {

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<?> handleLocked(AccountLockedException ex) {
        return ResponseEntity.status(HttpStatus.LOCKED) // 423 Locked
            .body(Map.of(
                "error", "Account Locked",
                "message", "Too many attempts. Try again in " + ex.getMinutesRemaining() + " minutes."
            ));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<?> handleInvalid(InvalidCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED) // 401 Unauthorized
            .body(Map.of(
                "error", "Invalid Credentials",
                "remainingAttempts", ex.getRemainingAttempts()
            ));
    }


@ExceptionHandler(BookstoreException.class)
public ResponseEntity<Map<String, Object>> handleBookStoreException(
        BookstoreException ex,
        HttpServletRequest request) {
    log.warn("BookstoreException [{}] at {}: {}", ex.getErrorCode(), request.getRequestURI(), ex.getMessage());
    HttpStatus status = Objects.requireNonNull(ex.getStatus());
    return ResponseEntity.status(status)
            .body(Map.of(
                    "status", ex.getStatus().value(),
                    "error", ex.getErrorCode(),
                    "message", ex.getMessage() != null ? ex.getMessage() : "",
                    "path", request.getRequestURI()));
}



@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<Map<String, Object>> handleValidation(
        MethodArgumentNotValidException ex,
        HttpServletRequest request) {
    List<Map<String, String>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> Map.of(
                    "field", fe.getField(),
                    "message", fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "",
                    "rejectedValue", fe.getRejectedValue() != null ? String.valueOf(fe.getRejectedValue()) : ""))
            .collect(Collectors.toList());

    return ResponseEntity.badRequest()
            .body(Map.of(
                    "status", HttpStatus.BAD_REQUEST.value(),
                    "error", "VALIDATION_FAILED",
                    "message", "Request validation failed. Check 'fieldErrors' for details.",
                    "path", request.getRequestURI(),
                    "fieldErrors", fieldErrors));
}

@ExceptionHandler(BadCredentialsException.class)
public ResponseEntity<?>  handleBadCredentials( BadCredentialsException ex, HttpServletRequest request){
    log.warn("Bad credentials attempt at {}", request.getRequestURI());

    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
        "status", HttpStatus.UNAUTHORIZED.value(),
        "error", ErrorCode.INVALID_CREDENTIALS, 
        "message", "Invalid username or password",
        "Path", request.getRequestURI()
    ));
}

@ExceptionHandler(DisabledException.class)
public ResponseEntity<?> handleDisabled(
            DisabledException ex, HttpServletRequest request) {
 
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(Map.of(
                        "status", HttpStatus.FORBIDDEN.value(),
                        "error",ErrorCode.ACCOUNT_LOCKED,
                        "message", ex.getMessage(),
                        "path", request.getRequestURI()
                ));
}


@ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
 
        log.warn("Access denied at {} for path {}",
                request.getRemoteAddr(), request.getRequestURI());
 
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(Map.of(
                       "status", HttpStatus.FORBIDDEN.value(),
                        "error", ErrorCode.FORBIDDEN,
                        "message", "You do not have permission to perform this action",
                        "path", request.getRequestURI()
                ));
    }


    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<?> handleMissingParam(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
 
        return ResponseEntity
                .badRequest()
                .body(Map.of(
                       "status",  HttpStatus.BAD_REQUEST.value(),
                        "error", ErrorCode.BAD_REQUEST,
                        "message","Required parameter '" + ex.getParameterName() + "' is missing",
                        "path", request.getRequestURI()
                ));
    }



    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<?> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        
        Class<?> requiredType  = ex.getRequiredType();
        String typeName = requiredType != null ? requiredType.getSimpleName() : "Unknown";
        String message = "Paremeter '" + ex.getName()  + "' should be  of type " + typeName;
 
        return ResponseEntity
                .badRequest()
                .body(Map.of(
                      "status",  HttpStatus.BAD_REQUEST.value(),
                        "error", ErrorCode.BAD_REQUEST,
                        "message", message,
                        "path",request.getRequestURI()
                ));
    }


    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleUnexpected(
            Exception ex, HttpServletRequest request) {
 
        // Log full stack trace internally — ops team needs this
        log.error("Unexpected error at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
 
        // But return a safe generic message to the client
        return ResponseEntity
                .internalServerError()
                .body(Map.of(
                       "status",  HttpStatus.INTERNAL_SERVER_ERROR.value(),
                       "error", ErrorCode.INTERNAL_ERROR,
                        "message", "An unexpected error occurred. Our team has been notified.",
                        "path", request.getRequestURI()
                ));
    }

}