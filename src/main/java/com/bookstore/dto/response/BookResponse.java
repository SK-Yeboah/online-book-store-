package com.bookstore.dto.response;

import java.time.LocalDateTime;
import com.bookstore.entity.Book;
import lombok.Builder;
import lombok.Getter;



@Getter
@Builder
public class BookResponse {

    private Long id;
    private String title;
    private String author;
    private String category;
    private String isbn;
    private Double price;
    private Integer stockQuantity;
    private String description;
    private boolean inStock;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static BookResponse from(Book book){
        return BookResponse.builder()
                                    .id(book.getId())
                                    .title(book.getTitle())
                                    .author(book.getAuthor())
                                    .category(book.getCategory())
                                    .isbn(book.getIsbn())
                                    .price(book.getPrice())
                                    .stockQuantity(book.getStockQuantity())
                                    .description(book.getDescription())
                                    .inStock(book.getStockQuantity() > 0)
                                    .createdAt(book.getCreatedAt())
                                    .updatedAt(book.getUpdatedAt())
                                    .build();
    }

    
}
