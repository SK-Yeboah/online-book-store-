package com.bookstore.payment;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.bookstore.config.PaystackProperties;
import com.bookstore.exception.BookstoreException;
import com.bookstore.exception.handler.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.MediaType;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@ConditionalOnProperty(name = "payment.provider", havingValue = "paystack")
public class PaystackPaymentProvider implements PaymentProvider{

    private final RestClient payStackRestClient;
    private final PaystackProperties properties;
    private final ObjectMapper objectMapper;

    public PaystackPaymentProvider(
            @Qualifier("payStackRestClient") RestClient payStackRestClient,
            PaystackProperties properties,
            ObjectMapper objectMapper) {
        this.payStackRestClient = payStackRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "paystack";
    }

    @Override
    public ProviderIntentResult createIntent(ProviderIntentRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", request.email());
        body.put("amount", toMinorUnits(request.amount()));
        body.put("reference", request.reference());
        body.put("currency", request.currency());

        if(request.callbackUrl() != null && !request.callbackUrl().isBlank()){
            body.put("callback_url", request.callbackUrl());
        }

        try{
            String responseBody = payStackRestClient.post()
                                                    .uri("/transaction/initialize")
                                                    .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                                                    .body(body)
                                                    .retrieve()
                                                    .onStatus(status -> status.isError(),(req, res) ->{
                                                        throw new BookstoreException(ErrorCode.BAD_REQUEST.name(), "Paystack initialize failed: HTTP " + res.getStatusCode(), HttpStatus.BAD_GATEWAY);
                                                    })
                                                    .body(String.class);
            JsonNode root = objectMapper.readTree(responseBody);
            if(!root.path("status").asBoolean(false)){
                throw new BookstoreException(
                    ErrorCode.BAD_REQUEST.name(),
                    "Paystack initialize failed: " + root.path("message").asText(),
                    HttpStatus.BAD_GATEWAY);
            }
            JsonNode data = root.path("data");
            return new ProviderIntentResult(
                    data.path("reference").asText(null),
                    data.path("authorization_url").asText(null),
                    data.path("access_code").asText(null),
                    null);
        }catch(BookstoreException ex){
            throw ex;
        }catch (Exception ex){
            log.error("Paystack initialize error", ex);
            throw new BookstoreException(
                    ErrorCode.INTERNAL_ERROR.name(),
                    "Failed to initialize Paystack payment",
                    HttpStatus.BAD_GATEWAY);
        }

      
    }

    @Override
    public ProviderWebhookResult parseAndVerifyWebhook(String rawBody, String signatureHeader) {
        
        if (!verifySignature(rawBody, signatureHeader)) {
            throw new BookstoreException(
                    ErrorCode.PAYMENT_WEBHOOK_INVALID.name(),
                    "Invalid Paystack webhook signature",
                    HttpStatus.BAD_REQUEST);
        }

        try {
            JsonNode root = objectMapper.readTree(rawBody);
            String event = root.path("event").asText("");
            JsonNode data = root.path("data");
            boolean success = "charge.success".equals(event);
            boolean failed = event.startsWith("charge.") && !success;
            if (!success && !failed) {
                throw new BookstoreException(
                        ErrorCode.PAYMENT_WEBHOOK_INVALID.name(),
                        "Unsupported Paystack event: " + event,
                        HttpStatus.BAD_REQUEST);
            }
            return new ProviderWebhookResult(new ProviderWebhookCommand(
                    String.valueOf(data.path("id").asLong()),
                    data.path("reference").asText(),
                    success,
                    event));
        } catch (BookstoreException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Paystack webhook parse error", ex);
            throw new BookstoreException(
                    ErrorCode.PAYMENT_WEBHOOK_INVALID.name(),
                    "Invalid Paystack webhook payload",
                    HttpStatus.BAD_REQUEST);
        }

    }

    @Override
    public boolean confirmPayment(String reference) {
        try {
            String responseBody = payStackRestClient.get()
                    .uri("/transaction/verify/{reference}", reference)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(responseBody);
            return root.path("status").asBoolean(false)
                    && "success".equalsIgnoreCase(root.path("data").path("status").asText());
        } catch (Exception ex) {
            log.warn("Paystack verify failed for reference={}", reference, ex);
            return false;
        }
    }


    private static long toMinorUnits(double amount) {
        return Math.round(amount * 100);
    }

    private boolean verifySignature(String rawBody, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(
                    properties.getSecretKey().getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            String computed = HexFormat.of().formatHex(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
            return java.security.MessageDigest.isEqual(
                    computed.getBytes(StandardCharsets.UTF_8),
                    signatureHeader.toLowerCase().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            log.warn("Paystack signature verification error", ex);
            return false;
        }
    }

    

    
}
