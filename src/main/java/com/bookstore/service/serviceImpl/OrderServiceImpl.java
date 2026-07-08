package com.bookstore.service.serviceImpl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookstore.dto.response.OrderResponse;
import com.bookstore.entity.Book;
import com.bookstore.entity.Cart;
import com.bookstore.entity.CartItem;
import com.bookstore.entity.Order;
import com.bookstore.entity.OrderItem;
import com.bookstore.exception.BookstoreException;
import com.bookstore.exception.ResourceNotFoundException;
import com.bookstore.exception.handler.ErrorCode;
import com.bookstore.repository.BookRepository;
import com.bookstore.repository.CartItemRepository;
import com.bookstore.repository.CartRepository;
import com.bookstore.repository.OrderItemRepository;
import com.bookstore.repository.OrderRepository;
import com.bookstore.service.OrderService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final BookRepository bookRepository;

    @Override
    @Transactional
    public OrderResponse checkout(Long userId) {
        Long id = Objects.requireNonNull(userId, "User id must not be null");

        Cart cart = cartRepository.findByUser_Id(id)
                .orElseThrow(() -> emptyCartException());

        List<CartItem> cartItems = cartItemRepository.findByCartIdWithBooks(cart.getId());
        if (cartItems.isEmpty()) {
            throw emptyCartException();
        }

        List<CartItem> sortedItems = cartItems.stream()
                .sorted(Comparator.comparing(item -> item.getBook().getId()))
                .toList();

        List<LockedLine> lockedLines = new ArrayList<>();
        for (CartItem cartItem : sortedItems) {
            Long bookId = cartItem.getBook().getId();
            Book book = bookRepository.findByIdForUpdate(bookId)
                    .orElseThrow(() -> new ResourceNotFoundException("Book", "id", bookId));
            int quantity = cartItem.getQuantity();
            validateStock(book, quantity);
            lockedLines.add(new LockedLine(book, quantity));
        }

        double totalAmount = lockedLines.stream()
                .mapToDouble(line -> line.book().getPrice() * line.quantity())
                .sum();

        Order order = orderRepository.save(new Order(cart.getUser(), totalAmount));

        for (LockedLine line : lockedLines) {
            Book book = line.book();
            int quantity = line.quantity();
            orderItemRepository.save(new OrderItem(order, book, quantity, book.getPrice()));
            book.setStockQuantity(book.getStockQuantity() - quantity);
        }

        cartItemRepository.deleteByCart_Id(cart.getId());
        log.info("Checkout complete: orderId={}, userId={}, total={}", order.getId(), id, totalAmount);

        return toOrderResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> findByUser(Long userId) {
        return orderRepository.findByUser_IdOrderByCreatedAtDesc(userId).stream()
                .map(this::toOrderResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse findById(Long userId, Long orderId) {
        Long id = Objects.requireNonNull(orderId, "Order id must not be null");
        Order order = orderRepository.findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("order", "id", id));
        return toOrderResponse(order);
    }

    private OrderResponse toOrderResponse(Order order) {
        List<OrderItem> items = orderItemRepository.findByOrderIdWithBooks(order.getId());
        return OrderResponse.from(order, items);
    }

    private void validateStock(Book book, int requestedQuantity) {
        if (requestedQuantity > book.getStockQuantity()) {
            throw new BookstoreException(
                    ErrorCode.INSUFFICIENT_STOCK.name(),
                    "Only " + book.getStockQuantity() + " copies available for \"" + book.getTitle() + "\"",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private BookstoreException emptyCartException() {
        return new BookstoreException(
                ErrorCode.EMPTY_CART.name(),
                "Cart is empty",
                HttpStatus.BAD_REQUEST);
    }

    private record LockedLine(Book book, int quantity) {}
}
