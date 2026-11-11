package rw.shop.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "inventory_movements")
public class InventoryMovement {

    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID businessId;
    @Column(nullable = false)
    private UUID productId;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "movement_type")
    private MovementType movementType;
    @Column(nullable = false)
    private BigDecimal quantityDelta;
    private UUID saleItemId;
    private UUID purchaseItemId;
    private UUID saleReturnItemId;
    private String reason;
    private UUID createdByUserId;
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

    public UUID getProductId() {
        return productId;
    }

    public void setProductId(UUID productId) {
        this.productId = productId;
    }

    public MovementType getMovementType() {
        return movementType;
    }

    public void setMovementType(MovementType movementType) {
        this.movementType = movementType;
    }

    public BigDecimal getQuantityDelta() {
        return quantityDelta;
    }

    public void setQuantityDelta(BigDecimal quantityDelta) {
        this.quantityDelta = quantityDelta;
    }

    public UUID getSaleItemId() {
        return saleItemId;
    }

    public void setSaleItemId(UUID saleItemId) {
        this.saleItemId = saleItemId;
    }

    public UUID getPurchaseItemId() {
        return purchaseItemId;
    }

    public void setPurchaseItemId(UUID purchaseItemId) {
        this.purchaseItemId = purchaseItemId;
    }

    public UUID getSaleReturnItemId() {
        return saleReturnItemId;
    }

    public void setSaleReturnItemId(UUID saleReturnItemId) {
        this.saleReturnItemId = saleReturnItemId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(UUID createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
