package pl.demo.redissqlshop.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Invoice {

    private long id;
    private long orderId;
    private String invoiceNumber;
    private LocalDateTime issuedAt;
    private BigDecimal totalPrice;

    public Invoice() {
    }

    public Invoice(long id, long orderId, String invoiceNumber, LocalDateTime issuedAt, BigDecimal totalPrice) {
        this.id = id;
        this.orderId = orderId;
        this.invoiceNumber = invoiceNumber;
        this.issuedAt = issuedAt;
        this.totalPrice = totalPrice;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getOrderId() {
        return orderId;
    }

    public void setOrderId(long orderId) {
        this.orderId = orderId;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(LocalDateTime issuedAt) {
        this.issuedAt = issuedAt;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }
}
