package com.bookstore.controller;


import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.bookstore.dto.request.CreatePaymentIntentRequest;
import com.bookstore.dto.response.PaymentIntentResponse;
import com.bookstore.dto.response.PaymentResponse;
import com.bookstore.exception.ResourceNotFoundException;
import com.bookstore.repository.UserRepository;
import com.bookstore.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Payment intents and webhooks")
public class PaymentController {

    private final PaymentService paymentService;
    private final UserRepository userRepository;


    @Operation(summary = "Create payment intent for an order")
    @PostMapping("/intent")
    public ResponseEntity<PaymentIntentResponse> createIntent(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreatePaymentIntentRequest request) {
        PaymentIntentResponse response = paymentService.createIntent(
                resolveUserId(userDetails), idempotencyKey, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Payment provider webhook")
    @PostMapping("/webhook/{provider}")
    public ResponseEntity<PaymentResponse> webhook(
            @PathVariable String provider,
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Paystack-Signature", required = false) String paystackSignature,
            @RequestHeader(value = "X-Webhook-Secret", required = false) String mockSecret) {
        String signature = paystackSignature != null ? paystackSignature : mockSecret;
        return ResponseEntity.ok(paymentService.handleWebhook(provider, rawBody, signature).orElse(null));
    }

    @Operation(summary = "Get latest payment for an order")
    @GetMapping("/order/{orderId}")
    public ResponseEntity<PaymentResponse> getByOrder(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long orderId) {
        return ResponseEntity.ok(paymentService.findByOrder(resolveUserId(userDetails), orderId));
    }

    @Operation(summary = "Verify payment with provider after redirect (webhook fallback)")
    @GetMapping("/verify/{reference}")
    public ResponseEntity<PaymentResponse> verify(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String reference) {
        return ResponseEntity.ok(paymentService.verifyByReference(resolveUserId(userDetails), reference));
    }

    private Long resolveUserId(UserDetails userDetails) {
        return userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User", "Username", userDetails.getUsername()))
                .getId();
    }

    
}
