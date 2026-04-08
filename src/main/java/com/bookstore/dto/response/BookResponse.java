package com.bookstore.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookResponse {

    private Long Id;
    private String title;
    private String author;
    private String category;
    private String isbn;
    private Double price;
    private Integer stockQuantity;
    private String description;
    private boolean inStock; 

    
}
