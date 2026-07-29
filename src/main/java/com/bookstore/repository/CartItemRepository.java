package com.bookstore.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bookstore.entity.CartItem;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    @Query("""
            SELECT ci FROM CartItem ci 
            JOIN FETCH ci.book 
            WHERE ci.cart.id = :cartId
            """)
    List<CartItem> findByCartIdWithBooks(@Param("cartId") Long cartId);

    Optional<CartItem> findByCart_IdAndBook_Id(Long cartId, Long bookId);

    void deleteByCart_IdAndBook_Id(Long cartId, Long bookId);

    void deleteByCart_Id(Long cartId);


    

    
}
