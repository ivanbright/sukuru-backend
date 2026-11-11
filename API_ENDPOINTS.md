# Complete API Endpoints Reference

## Authentication

| Method | Endpoint | Description | Roles |
|--------|----------|-------------|-------|
| POST | `/api/v1/auth/register-business` | Register new business with owner account | Public |
| POST | `/api/v1/auth/login` | Login with email, password, businessId | Public |
| POST | `/api/v1/auth/refresh` | Refresh access token | Authenticated |
| POST | `/api/v1/auth/logout` | Logout and revoke refresh token | Authenticated |

---

## Business Management ✨ NEW

| Method | Endpoint | Description | Roles |
|--------|----------|-------------|-------|
| GET | `/api/v1/business` | Get current business details | All |
| PUT | `/api/v1/business` | Update business information | Owner |

---

## Memberships

| Method | Endpoint | Description | Roles |
|--------|----------|-------------|-------|
| GET | `/api/v1/memberships` | List business memberships | Owner |
| POST | `/api/v1/memberships` | Create new membership | Owner |
| PUT | `/api/v1/memberships/{id}` | Update membership role/status | Owner |

---

## Categories

| Method | Endpoint | Description | Roles |
|--------|----------|-------------|-------|
| GET | `/api/v1/categories` | List categories (with search) | All |
| GET | `/api/v1/categories/{id}` | Get category details | All |
| POST | `/api/v1/categories` | Create new category | Owner, Manager |
| PUT | `/api/v1/categories/{id}` | Update category | Owner, Manager |
| DELETE | `/api/v1/categories/{id}` | Soft delete category ✨ NEW | Owner, Manager |

---

## Products

| Method | Endpoint | Description | Roles |
|--------|----------|-------------|-------|
| GET | `/api/v1/products` | List products (with search) | All |
| GET | `/api/v1/products/{id}` | Get product details | All |
| POST | `/api/v1/products` | Create new product | Owner, Manager |
| PUT | `/api/v1/products/{id}` | Update product | Owner, Manager |

**Note:** Staff users don't see `purchasePrice` in responses

---

## Customers

| Method | Endpoint | Description | Roles |
|--------|----------|-------------|-------|
| GET | `/api/v1/customers` | List customers (with search) | All |
| GET | `/api/v1/customers/{id}` | Get customer details | All |
| POST | `/api/v1/customers` | Create new customer | Owner, Manager |
| PUT | `/api/v1/customers/{id}` | Update customer | Owner, Manager |
| DELETE | `/api/v1/customers/{id}` | Soft delete customer ✨ NEW | Owner, Manager |

---

## Suppliers

| Method | Endpoint | Description | Roles |
|--------|----------|-------------|-------|
| GET | `/api/v1/suppliers` | List suppliers (with search) | Owner, Manager |
| GET | `/api/v1/suppliers/{id}` | Get supplier details | Owner, Manager |
| POST | `/api/v1/suppliers` | Create new supplier | Owner, Manager |
| PUT | `/api/v1/suppliers/{id}` | Update supplier | Owner, Manager |
| DELETE | `/api/v1/suppliers/{id}` | Soft delete supplier ✨ NEW | Owner, Manager |

---

## Inventory

| Method | Endpoint | Description | Roles |
|--------|----------|-------------|-------|
| GET | `/api/v1/inventory/stock` | View current stock levels | All |
| POST | `/api/v1/inventory/adjustments` | Create stock adjustment | Owner, Manager |
| GET | `/api/v1/inventory/movements` | View inventory movement history | All |

**Query Parameters for stock:**
- `search` - Search stock by product name or SKU
- `page`, `size` - Pagination

**Query Parameters for movements:**
- `productId` - Filter by product
- `type` - Filter by movement type (purchase, sale, adjustment, return)
- `from`, `to` - Date/time range filter (ISO 8601 date-time)
- `page`, `size` - Pagination

---

## Purchases

| Method | Endpoint | Description | Roles |
|--------|----------|-------------|-------|
| GET | `/api/v1/purchases` | List purchases | Owner, Manager |
| GET | `/api/v1/purchases/{id}` | Get purchase details | Owner, Manager |
| POST | `/api/v1/purchases` | Create draft purchase | Owner, Manager |
| PUT | `/api/v1/purchases/{id}` | Update draft purchase | Owner, Manager |
| POST | `/api/v1/purchases/{id}/receive` | Mark purchase as received | Owner, Manager |
| POST | `/api/v1/purchases/{id}/cancel` | Cancel draft purchase | Owner, Manager |
| GET | `/api/v1/purchases/{id}/payments` | List purchase payments | Owner, Manager |
| POST | `/api/v1/purchases/{id}/payments` | Add purchase payment | Owner, Manager |

**Note:** 
- All authenticated `POST /api/v1/**` requests outside `/api/v1/auth/**` require an `Idempotency-Key` UUID, including receive, cancel, and payment actions
- Receiving creates inventory movements and updates product purchase prices

---

## Sales

| Method | Endpoint | Description | Roles |
|--------|----------|-------------|-------|
| GET | `/api/v1/sales` | List sales | All |
| GET | `/api/v1/sales/{id}` | Get sale details | All |
| POST | `/api/v1/sales` | Create completed sale | All |
| GET | `/api/v1/sales/{id}/payments` | List sale payments | All |
| POST | `/api/v1/sales/{id}/payments` | Add sale payment | All |

**Note:**
- Creating sales and adding sale payments require an `Idempotency-Key` UUID
- Sales are immediately completed (no draft state)
- Stock is validated and decremented atomically
- Auto-generates reference numbers (S-000001, S-000002, etc.)
- Staff can only see their own sales
- Staff don't see `unitCost` in responses

---

## Returns ✨ NEW

| Method | Endpoint | Description | Roles |
|--------|----------|-------------|-------|
| GET | `/api/v1/returns` | List sale returns | All |
| GET | `/api/v1/returns/{id}` | Get return details | All |
| POST | `/api/v1/returns` | Create sale return | All |

**Query Parameters for list:**
- `saleId` - Filter by specific sale
- `page`, `size` - Pagination

**Note:**
- Creating returns requires `Idempotency-Key` header
- Can only return items from completed sales
- Auto-generates reference numbers (R-000001, R-000002, etc.)
- Automatically restocks inventory
- Supports refund methods: cash, mobile_money, bank_transfer, store_credit

---

## Reports

| Method | Endpoint | Description | Roles |
|--------|----------|-------------|-------|
| GET | `/api/v1/reports/summary` | Business summary dashboard | Owner, Manager |
| GET | `/api/v1/reports/sales` | Sales by period report | Owner, Manager |
| GET | `/api/v1/reports/top-products` | Top-selling products | Owner, Manager |
| GET | `/api/v1/reports/low-stock` | Low stock alert report | Owner, Manager |

**Query Parameters:**
- `from`, `to` - Date/time range (ISO 8601 date-time)
- `groupBy` - For sales report: day, week, month
- `limit` - For top-products report: maximum number of products
- `threshold` - For low stock report: minimum quantity

---

## Audit Logs

| Method | Endpoint | Description | Roles |
|--------|----------|-------------|-------|
| GET | `/api/v1/audit-logs` | View audit trail | Owner |

**Query Parameters:**
- `entityType` - Filter by type (product, sale, purchase, etc.)
- `entityId` - Filter by specific entity
- `action` - Filter by action (create, update, delete, etc.)
- `actorUserId` - Filter by user who performed action
- `startDate`, `endDate` - Date range filter
- `page`, `size` - Pagination

---

## Request/Response Format

### Request Headers
```
Authorization: Bearer <access_token>
Content-Type: application/json
Idempotency-Key: <uuid>  (required for authenticated POST requests outside /api/v1/auth/)
```

### Money Format
All amounts are strings in decimal format:
```json
{
  "amount": "1250.00",
  "sellingPrice": "3500.50"
}
```

### Quantity Format
All quantities are strings in decimal format with up to 3 decimal places:
```json
{
  "quantity": "5.000",
  "quantityReturned": "2.500"
}
```

### Pagination
```json
{
  "items": [...],
  "page": 0,
  "size": 20,
  "totalItems": 156
}
```

### Error Response
```json
{
  "code": "VALIDATION_ERROR",
  "message": "Product not found",
  "timestamp": "2026-11-07T10:30:00Z"
}
```

---

## Error Codes

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `VALIDATION_ERROR` | 400 | Invalid request data |
| `UNAUTHORIZED` | 401 | Not authenticated |
| `FORBIDDEN` | 403 | Insufficient permissions |
| `NOT_FOUND` | 404 | Resource not found |
| `CONFLICT` | 409 | Business logic conflict |
| `INSUFFICIENT_STOCK` | 409 | Not enough stock for sale |
| `IDEMPOTENCY_KEY_REUSED` | 422 | Same key with different request |
| `RATE_LIMITED` | 429 | Too many requests |

---

## Total Endpoints: 54
- Authentication: 4
- Business: 2 ✨ NEW
- Memberships: 3
- Categories: 5 (1 new)
- Products: 4
- Customers: 5 (1 new)
- Suppliers: 5 (1 new)
- Inventory: 3
- Purchases: 8
- Sales: 5
- Returns: 3 ✨ NEW
- Reports: 4
- Audit: 1

---

## Security Features

✅ JWT access tokens (15 min expiry)  
✅ Rotating refresh tokens (30 day expiry)  
✅ Rate limiting on login endpoints  
✅ Row-level security (PostgreSQL RLS)  
✅ Role-based access control  
✅ Idempotency for mutations  
✅ Tenant isolation enforced at DB level  
✅ Audit trail for all changes  

---

## Next: Swagger UI

Access interactive API documentation at:
```
http://localhost:8080/swagger-ui.html
```

This provides:
- Complete request/response schemas
- Try-it-out functionality
- Authentication testing
- Example requests
