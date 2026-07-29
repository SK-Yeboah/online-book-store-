package com.bookstore.repository;

import java.util.Optional;

import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;


import com.bookstore.entity.Book;

import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface BookRepository extends JpaRepository<Book, Long> {
    boolean existsByIsbn(String isbn);
    Optional<Book> findByIsbn(String isbn);

    @Query("""
                SELECT b FROM Book b 
                WHERE (:category    IS NULL     OR LOWER(b.category) = LOWER(:category))
                AND(:author         IS NULL     OR LOWER(b.author)  LIKE LOWER(CONCAT('%', :author, '%')))
                AND(:search         IS NULL     OR LOWER(b.title)   LIKE LOWER(CONCAT('%', :search, '%'))
                                                OR LOWER(b.author)  LIKE LOWER(CONCAT('%', :search, '%'))
                                                OR LOWER(b.isbn)    LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<Book> findByFilters(
        @Param("category")  String category,
        @Param("author")    String author,
        @Param("search")    String search,
        Pageable pageable);


        // Pessimistic Write Lock - Prevents concurrent oversell on stock adjustment
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT b FROM Book b WHERE b.id = :id")
        Optional<Book> findByIdForUpdate(@Param("id") Long id);
    
} 