package com.bookstore.service.serviceImpl;

import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.bookstore.dto.request.AddToCartRequest;
import com.bookstore.dto.request.UpdateCartItemRequest;
import com.bookstore.dto.response.CartResponse;
import com.bookstore.entity.Book;
import com.bookstore.entity.Cart;
import com.bookstore.entity.CartItem;
import com.bookstore.entity.User;
import com.bookstore.exception.BookstoreException;
import com.bookstore.exception.ResourceNotFoundException;
import com.bookstore.exception.handler.ErrorCode;
import com.bookstore.repository.BookRepository;
import com.bookstore.repository.CartRepository;
import com.bookstore.repository.UserRepository;
import com.bookstore.repository.CartItemRepository;
import com.bookstore.service.CartService;
import org.springframework.transaction.annotation.Transactional;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartServiceImpl  implements CartService{
    
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository; 
    private final BookRepository bookRepository;
    private final UserRepository userRepository;

    // Pulic methods
    @Override
    @Transactional(readOnly = true)
    public CartResponse getCart(Long userId) {
        Cart cart  = getOrCreateCart(userId);
        return loadCartResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse addItem(Long userId, AddToCartRequest request) {
        Cart cart  = getOrCreateCart(userId);
        Book book  = getBookOrThrow(request.getBookId());

        int qty = request.getQuantity();

        var existing = cartItemRepository.findByCart_IdAndBook_Id(cart.getId(), book.getId());

        if(existing.isPresent()){
            CartItem item = existing.orElseThrow();
            int newQty = existing.get().getQuantity() + qty;
            validate(book, newQty);
            item.setQuantity(newQty);
            cartItemRepository.save(item);
            
        }else{
            validate(book, qty);
            cartItemRepository.save(new CartItem(cart, book, qty));
        }

        return loadCartResponse(cart);

    }

    @Override
    @Transactional
    public CartResponse updateItemQuantity(Long userId, Long bookId, UpdateCartItemRequest request) {
       Cart cart = getOrCreateCart(userId);
       Book book = getBookOrThrow(bookId);
       CartItem item = getCartItemOrThrow(cart.getId(), book.getId());

       int newQty = request.getQuantity();
       validate(book, newQty);

       item.setQuantity(newQty);
       cartItemRepository.save(item);
       log.info("cart quantity updated: cartId = {}, bookId={}, quantity={}", cart.getId(), bookId, newQty);

       return loadCartResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse removeItem(Long userId, Long bookId) {
        Cart cart = getOrCreateCart(userId);
        getBookOrThrow(bookId);

        if(!cartItemRepository.findByCart_IdAndBook_Id(cart.getId(), bookId).isPresent()){
            throw new ResourceNotFoundException("Cart Item", "bookId", bookId);
        }

        cartItemRepository.deleteByCart_IdAndBook_Id(cart.getId(), bookId);
        log.info("Remove from cart: cartId={}, bookId={}", cart.getId(), bookId);

        return loadCartResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse clearCart(Long userId) {
        Cart cart = getOrCreateCart(userId);
        cartItemRepository.deleteByCart_Id(cart.getId());
        log.info("Cart cleared: cartId={}", cart.getId());
        return loadCartResponse(cart);
    }


    //Helpers
    /** Find user's cart or creat an empty one on first access. */
    private Cart getOrCreateCart(Long userId){
        
        Long id = Objects.requireNonNull(userId, "User id must not be null");
        

        return cartRepository.findByUser_Id(userId).orElseGet(() -> {
            User user = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
            Cart cart = cartRepository.save(new Cart(user));
            log.info("Created Cart id{} for userId={}", cart.getId(), id);
            return cart;
        });

    }

    /** Load cart items (with books) and build the response DTO */
    private CartResponse loadCartResponse(Cart cart) {
        List<CartItem> items = cartItemRepository.findByCartIdWithBooks(cart.getId());
        
        return CartResponse.from(cart.getId(), items);
    }

    /** Soft Stock check - used when adding/updating cart (hard lock happens at checkout) */
    private void validate(Book book, int requestQuantity){
        if(requestQuantity > book.getStockQuantity()){
            throw new BookstoreException(ErrorCode.INSUFFICIENT_STOCK.name(), "Only" + book.getStockQuantity()+" copies available for \"" + book.getTitle() + "\"", HttpStatus.BAD_REQUEST);
        }
    }

    private Book getBookOrThrow(Long bookId){
        Long id = Objects.requireNonNull(bookId, "Book id must not be null");
        return bookRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Book", "id", bookId));
    }

    private CartItem getCartItemOrThrow(Long cartId, Long bookId){
        return cartItemRepository.findByCart_IdAndBook_Id(cartId, bookId)
                                .orElseThrow(() -> new ResourceNotFoundException("Cart Item", "BookId", bookId));
    }

    
    
}
