package com.bookstore.exception.handler;

// ─────────────────────────────────────────────────────────────────────
// ErrorCode — machine-readable codes for every error in the system.
//
// WHY enum codes instead of just HTTP status codes?
//
//   HTTP 400 alone is ambiguous — is it a validation error?
//   Duplicate email? Missing field? Insufficient stock?
//
//   With error codes the client knows EXACTLY what happened:
//     "DUPLICATE_EMAIL"      → show "Email already registered" UI
//     "INSUFFICIENT_STOCK"   → show "Only X copies left" UI
//     "ACCOUNT_LOCKED"       → show "Try again in 15 min" UI
//
//   This is how payment APIs (Stripe), cloud APIs (AWS), and
//   enterprise APIs communicate errors — not just HTTP status codes.
// ─────────────────────────────────────────────────────────────────────
public enum ErrorCode {

    // ── Auth ──────────────────────────────────────────────────────
    INVALID_CREDENTIALS,
    ACCOUNT_LOCKED,
    TOKEN_EXPIRED,
    TOKEN_INVALID,
    TOKEN_REVOKED,
    REFRESH_TOKEN_INVALID,
    REFRESH_TOKEN_EXPIRED,
    REFRESH_TOKEN_REUSE_DETECTED,
    UNAUTHORIZED,

    // ── Resource ──────────────────────────────────────────────────
    USER_NOT_FOUND,
    BOOK_NOT_FOUND,
    CART_NOT_FOUND,
    CART_ITEM_NOT_FOUND,
    ORDER_NOT_FOUND,
    PAYMENT_NOT_FOUND,

    // ── Duplicate ─────────────────────────────────────────────────
    DUPLICATE_USERNAME,
    DUPLICATE_EMAIL,
    DUPLICATE_ISBN,

    // ── Business Logic ────────────────────────────────────────────
    INSUFFICIENT_STOCK,
    EMPTY_CART,
    ORDER_ALREADY_CANCELLED,
    INVALID_ORDER_STATUS_TRANSITION,

    // ── Security ──────────────────────────────────────────────────
    ACCESS_DENIED,
    FORBIDDEN,

    // ── Validation ────────────────────────────────────────────────
    VALIDATION_FAILED,

    // ── General ───────────────────────────────────────────────────
    INTERNAL_ERROR,
    BAD_REQUEST,

    // ----- Payment Codes ------------
    ORDER_NOT_PAYABLE,
    INVALID_PAYMENT_STATE,
    PAYMENT_ALREADY_SUCCEEDED,
    PAYMENT_WEBHOOK_INVALID,
}