package com.bookstore.controller;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bookstore.dto.request.UpdateOrderStatusRequest;
import com.bookstore.dto.response.OrderResponse;
import com.bookstore.dto.response.PagedResponse;
import com.bookstore.service.OrderService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
@Tag(name = "Admin Orders", description = "Admin order fulfillment operations")
public class AdminOrderController {

    private final OrderService orderService;

    @Operation(summary = "List all orders", description = "Admin only. Paginated, newest first.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<PagedResponse<OrderResponse>> listOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(orderService.findAll(pageable));
    }

    @Operation(summary = "Update order status", description = "Admin only. Validates status transitions.")
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        OrderResponse response = orderService.updateStatus(id, request);
        log.info("Admin updated order id={} to status={}", id, response.getStatus());
        return ResponseEntity.ok(response);
    }
}
