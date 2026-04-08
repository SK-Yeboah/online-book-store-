package com.bookstore.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;

@Data
@Entity
@Table(name= "orders")
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Order extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Double totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus  status = OrderStatus.PENDING;

    public Order(User user, Double totalAmount){
        this.user = user;
        this.totalAmount = totalAmount;

    }


    public enum OrderStatus {
        PENDING,
        CONFIRMED, 
        SHIPPED, 
        DELIVERED, 
        CANCELLED
    }


    
}
