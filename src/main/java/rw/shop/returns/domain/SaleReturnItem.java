package rw.shop.returns.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sale_return_items")
public class SaleReturnItem {

    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID businessId;
    @Column(nullable = false)
    private UUID saleReturnId;
    @Column(nullable = false)
    private UUID saleItemId;
    @Column(nullable = false)
    private UUID productId;
    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantityReturned;
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice;
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal;
    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public void setBusinessId(UUID businessId) {
        this.businessId = businessId;
    }

    public UUID getSaleReturnId() {
        return saleReturnId;
    }

    public void setSaleReturnId(UUID saleReturnId) {
        this.saleReturnId = saleReturnId;
    }

    public UUID getSaleItemId() {
        return saleItemId;
    }

    public void setSaleItemId(UUID saleItemId) {
        this.saleItemId = saleItemId;
    }

    public UUID getProductId() {
        return productId;
    }

    public void setProductId(UUID productId) {
        this.productId = productId;
    }

    public BigDecimal getQuantityReturned() {
        return quantityReturned;
    }

    public void setQuantityReturned(BigDecimal quantityReturned) {
        this.quantityReturned = quantityReturned;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
