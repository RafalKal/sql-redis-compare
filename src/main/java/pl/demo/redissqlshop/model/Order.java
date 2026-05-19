package pl.demo.redissqlshop.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Order {

    private long id;
    private long userId;
    private LocalDateTime createdAt;
    private BigDecimal totalPrice;

    public Order() {
    }

    public Order(long id, long userId, LocalDateTime createdAt, BigDecimal totalPrice) {
        this.id = id;
        this.userId = userId;
        this.createdAt = createdAt;
        this.totalPrice = totalPrice;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }
}
