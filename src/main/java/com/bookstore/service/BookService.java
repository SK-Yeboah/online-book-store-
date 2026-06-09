package com.bookstore.service;

import org.springframework.data.domain.Pageable;

import com.bookstore.dto.response.PagedResponse;
import com.bookstore.dto.request.BookRequest;
import com.bookstore.dto.request.UpdateStockRequest;
import com.bookstore.dto.response.BookResponse;

public interface BookService {

    PagedResponse<BookResponse> findAll(String category, String author, String search, Pageable pageable);
    BookResponse findById(Long id);
    BookResponse findByIsbn(String isbn);
    BookResponse create(BookRequest request);
    BookResponse update(Long id, BookRequest request);
    BookResponse updateStock(Long id, UpdateStockRequest request);
    void delete(Long id);
} 