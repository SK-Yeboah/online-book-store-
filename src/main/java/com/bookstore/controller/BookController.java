package com.bookstore.controller;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.bookstore.dto.request.BookRequest;
import com.bookstore.dto.request.UpdateStockRequest;
import com.bookstore.dto.response.BookResponse;
import com.bookstore.dto.response.PagedResponse;
import com.bookstore.exception.handler.ErrorResponse;
import com.bookstore.service.BookService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Books", description = "Browse and manage the book catalogue")
public class BookController {

    private final BookService bookService;

    // ── Public ───────────────────────────────────────────────────────────────

    @Operation(summary = "List books", description = "Paginated list with optional category, author, and search filters.")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "Success") })
    @SecurityRequirements
    @GetMapping("/api/books")
    public ResponseEntity<PagedResponse<BookResponse>> listBooks(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "title") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), sort);

        return ResponseEntity.ok(bookService.findAll(category, author, search, pageable));
    }

    @Operation(summary = "Get book by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Found"),
        @ApiResponse(responseCode = "404", description = "Not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirements
    @GetMapping("/api/books/{id}")
    public ResponseEntity<BookResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(bookService.findById(id));
    }

    @Operation(summary = "Get book by ISBN")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Found"),
        @ApiResponse(responseCode = "404", description = "Not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirements
    @GetMapping("/api/books/isbn/{isbn}")
    public ResponseEntity<BookResponse> getByIsbn(@PathVariable String isbn) {
        return ResponseEntity.ok(bookService.findByIsbn(isbn));
    }

    // ── Admin ────────────────────────────────────────────────────────────────

    @Operation(summary = "Create a book", description = "Admin only.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/api/admin/books")
    public ResponseEntity<BookResponse> create(@Valid @RequestBody BookRequest request) {
        BookResponse response = bookService.create(request);
        log.info("Admin created book id={}", response.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Update a book", description = "Admin only. Full replace.")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/api/admin/books/{id}")
    public ResponseEntity<BookResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody BookRequest request) {
        return ResponseEntity.ok(bookService.update(id, request));
    }

    @Operation(summary = "Update stock", description = "Admin only. Uses pessimistic lock.")
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/api/admin/books/{id}/stock")
    public ResponseEntity<BookResponse> updateStock(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStockRequest request) {
        return ResponseEntity.ok(bookService.updateStock(id, request));
    }

    @Operation(summary = "Delete a book", description = "Admin only.")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/api/admin/books/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        bookService.delete(id);
        return ResponseEntity.noContent().build();
    }
}