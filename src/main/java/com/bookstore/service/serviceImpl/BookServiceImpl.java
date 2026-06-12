package com.bookstore.service.serviceImpl;


import org.springframework.stereotype.Service;

import com.bookstore.dto.request.BookRequest;
import com.bookstore.dto.request.UpdateStockRequest;
import com.bookstore.dto.response.BookResponse;
import com.bookstore.dto.response.PagedResponse;
import com.bookstore.entity.Book;
import com.bookstore.exception.DuplicateResourceException;
import com.bookstore.exception.ResourceNotFoundException;
import com.bookstore.repository.BookRepository;
import com.bookstore.service.BookService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Objects;


@Slf4j
@Service
@RequiredArgsConstructor
public class BookServiceImpl  implements BookService{

    private final BookRepository bookRepository;

    // Read Operations
    @Override
    @Transactional(readOnly = true)
    public PagedResponse<BookResponse> findAll(
        String category, 
        String author, 
        String search,
        Pageable pageable
    ) {
                Page<BookResponse> page = bookRepository.findByFilters(category, author, search, pageable).map(BookResponse::from);
                return PagedResponse.of(page);
      
    }

    

    @Override
    @Transactional(readOnly = true)
    public BookResponse findById(Long id) {
      return BookResponse.from(getOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public BookResponse findByIsbn(String isbn) {
       return bookRepository.findByIsbn(isbn)
                            .map(BookResponse::from)
                            .orElseThrow(() -> new ResourceNotFoundException("book", "isbn", isbn));
    }

    // Write Operations

    @Override
    @Transactional
    public BookResponse create(BookRequest request) {
        if(bookRepository.existsByIsbn(request.getIsbn())){
            throw new DuplicateResourceException("Book", "isbn", request.getIsbn());
        }
        Book book = new Book(
            request.getTitle(),
            request.getAuthor(),
            request.getCategory(),
            request.getIsbn(),
            request.getPrice(),
            request.getStockQuantity(),
            request.getDescription()
        );
        Book saved = bookRepository.save(book);
        log.info("Book Created: id={}, isbn={}", saved.getId(), saved.getIsbn());
        return BookResponse.from(saved);
    }

    @Override
    @Transactional
    public BookResponse update(Long id, BookRequest request) {
        Book book = getOrThrow(id);
        

        //Allow isbn change only if the new ISBN isn't taken by different book
        if(!book.getIsbn().equals(request.getIsbn()) && bookRepository.existsByIsbn((request.getIsbn()))){
            throw new DuplicateResourceException("Book", "isbn", request.getIsbn());
        }

        book.setTitle(request.getTitle());
        book.setAuthor(request.getAuthor());
        book.setCategory(request.getCategory());
        book.setIsbn(request.getIsbn());
        book.setPrice(request.getPrice());
        book.setStockQuantity(request.getStockQuantity());
        book.setDescription(request.getDescription());

        log.info("Book updated: id={}", id);
        return BookResponse.from(bookRepository.save(book));
    }

   
    @Override
    @Transactional
    public BookResponse updateStock(Long id, UpdateStockRequest request) {
        // Pessimistic write lock — safe for concurrent stock adjustments
        Book book = bookRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Book", "id", id));
        book.setStockQuantity(request.getStockQuantity());
        log.info("Stock updated: id={}, newStock={}", id, request.getStockQuantity());
        return BookResponse.from(bookRepository.save(book));
    }

    @Override
    @Transactional
    public void delete(Long id) {
       bookRepository.delete(Objects.requireNonNull(getOrThrow(id)));
       log.info("Book deleted: id={} ", id);
    }

    // Helpers
    private Book getOrThrow(Long id) {
        Long bookId = Objects.requireNonNull(id, "Book id must not be null");
    
        return Objects.requireNonNull(bookRepository.findById(bookId)
                .orElseThrow(() -> new ResourceNotFoundException("Book", "id", bookId)));
    }


  
   
    
}
