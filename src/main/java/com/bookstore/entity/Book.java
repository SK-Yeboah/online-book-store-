package com.bookstore.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "books")
public class Book extends BaseEntity{

    @NotBlank
    @Column(nullable = false)
    private String title;

    @NotBlank
    @Column(nullable = false)
    private String author;

    @NotBlank
    private String category; 

    @NotBlank
    @Column(unique = true, nullable = false)
    private String isbn;

    @NotNull
    @Column(nullable = false)
    @DecimalMin("0.0")
    private Double price;

    @NotNull
    @Min(0)
    @Column(nullable = false)
    private Integer stockQuantity;

    @Column(length = 2000)
    private String description;

    public Book(
        String title,
        String author,
        String category,
        String isbn,
        Double price,
        Integer stockQuantity,
        String description
    ){
        this.title = title;
        this.author = author;
        this.category = category;
        this.isbn = isbn;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.description = description;

    }


    
}
