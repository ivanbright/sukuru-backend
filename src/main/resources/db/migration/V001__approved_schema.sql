CREATE TYPE membership_role AS ENUM ('owner', 'manager', 'staff');
CREATE TYPE sale_status AS ENUM ('draft', 'completed', 'cancelled');
CREATE TYPE purchase_status AS ENUM ('draft', 'received', 'cancelled');
CREATE TYPE payment_method AS ENUM ('cash', 'mobile_money', 'bank_transfer', 'other');
CREATE TYPE movement_type AS ENUM ('purchase', 'sale', 'adjustment', 'return');

CREATE TABLE businesses (
    id uuid PRIMARY KEY,
    name varchar(150) NOT NULL,
    business_type varchar(100),
    phone varchar(30),
    email varchar(150),
    address text,
    country varchar(100),
    currency varchar(10) DEFAULT 'RWF',
    created_at timestamptz DEFAULT now(),
    updated_at timestamptz
);

CREATE TABLE users (
    id uuid PRIMARY KEY,
    full_name varchar(150) NOT NULL,
    email varchar(150) NOT NULL,
    password_hash varchar(255) NOT NULL,
    phone varchar(30),
    is_active boolean DEFAULT true,
    created_at timestamptz DEFAULT now(),
    updated_at timestamptz
);

CREATE UNIQUE INDEX users_email_lower_uq ON users (lower(email));

CREATE TABLE memberships (
    id uuid PRIMARY KEY,
    business_id uuid NOT NULL,
    user_id uuid NOT NULL,
    role membership_role NOT NULL,
    is_active boolean NOT NULL DEFAULT true,
    joined_at timestamptz DEFAULT now()
);

CREATE UNIQUE INDEX memberships_business_id_user_id_uq ON memberships (business_id, user_id);
CREATE UNIQUE INDEX memberships_business_id_id_uq ON memberships (business_id, id);

CREATE TABLE customers (
    id uuid PRIMARY KEY,
    business_id uuid NOT NULL,
    name varchar(150) NOT NULL,
    phone varchar(30),
    email varchar(150),
    address text,
    created_at timestamptz DEFAULT now()
);

CREATE UNIQUE INDEX customers_business_id_id_uq ON customers (business_id, id);

CREATE TABLE suppliers (
    id uuid PRIMARY KEY,
    business_id uuid NOT NULL,
    name varchar(150) NOT NULL,
    phone varchar(30),
    email varchar(150),
    address text,
    created_at timestamptz DEFAULT now()
);

CREATE UNIQUE INDEX suppliers_business_id_id_uq ON suppliers (business_id, id);

CREATE TABLE categories (
    id uuid PRIMARY KEY,
    business_id uuid NOT NULL,
    name varchar(100) NOT NULL,
    description text,
    created_at timestamptz DEFAULT now()
);

CREATE UNIQUE INDEX categories_business_id_id_uq ON categories (business_id, id);

CREATE TABLE products (
    id uuid PRIMARY KEY,
    business_id uuid NOT NULL,
    category_id uuid,
    name varchar(150) NOT NULL,
    sku varchar(100),
    description text,
    unit varchar(30) NOT NULL,
    purchase_price decimal(14,2),
    selling_price decimal(14,2),
    is_active boolean DEFAULT true,
    created_at timestamptz DEFAULT now(),
    updated_at timestamptz
);

CREATE UNIQUE INDEX products_business_id_id_uq ON products (business_id, id);
CREATE UNIQUE INDEX products_business_id_sku_uq ON products (business_id, sku);

CREATE TABLE sales (
    id uuid PRIMARY KEY,
    business_id uuid NOT NULL,
    customer_id uuid,
    created_by_user_id uuid NOT NULL,
    reference_number varchar(100),
    status sale_status DEFAULT 'draft',
    total_amount decimal(14,2) NOT NULL,
    notes text,
    sold_at timestamptz,
    created_at timestamptz DEFAULT now()
);

CREATE UNIQUE INDEX sales_business_id_id_uq ON sales (business_id, id);

CREATE TABLE sale_items (
    id uuid PRIMARY KEY,
    business_id uuid NOT NULL,
    sale_id uuid NOT NULL,
    product_id uuid NOT NULL,
    quantity decimal(14,3) NOT NULL,
    unit_price decimal(14,2) NOT NULL,
    unit_cost decimal(14,2) NOT NULL,
    subtotal decimal(14,2) NOT NULL,
    created_at timestamptz DEFAULT now()
);

CREATE UNIQUE INDEX sale_items_business_id_id_product_id_uq ON sale_items (business_id, id, product_id);

CREATE TABLE purchases (
    id uuid PRIMARY KEY,
    business_id uuid NOT NULL,
    supplier_id uuid,
    created_by_user_id uuid NOT NULL,
    reference_number varchar(100),
    status purchase_status DEFAULT 'draft',
    total_amount decimal(14,2) NOT NULL,
    notes text,
    purchased_at timestamptz,
    created_at timestamptz DEFAULT now()
);

CREATE UNIQUE INDEX purchases_business_id_id_uq ON purchases (business_id, id);

CREATE TABLE purchase_items (
    id uuid PRIMARY KEY,
    business_id uuid NOT NULL,
    purchase_id uuid NOT NULL,
    product_id uuid NOT NULL,
    quantity decimal(14,3) NOT NULL,
    unit_cost decimal(14,2) NOT NULL,
    subtotal decimal(14,2) NOT NULL,
    created_at timestamptz DEFAULT now()
);

CREATE UNIQUE INDEX purchase_items_business_id_id_product_id_uq ON purchase_items (business_id, id, product_id);

CREATE TABLE inventory_movements (
    id uuid PRIMARY KEY,
    business_id uuid NOT NULL,
    product_id uuid NOT NULL,
    movement_type movement_type NOT NULL,
    quantity_delta decimal(14,3) NOT NULL,
    sale_item_id uuid,
    purchase_item_id uuid,
    reason text,
    created_by_user_id uuid,
    created_at timestamptz DEFAULT now(),
    CONSTRAINT movement_type_consistency CHECK (
        (movement_type = 'sale'       AND quantity_delta < 0 AND sale_item_id IS NOT NULL AND purchase_item_id IS NULL)
     OR (movement_type = 'purchase'   AND quantity_delta > 0 AND purchase_item_id IS NOT NULL AND sale_item_id IS NULL)
     OR (movement_type IN ('adjustment','return') AND quantity_delta <> 0 AND sale_item_id IS NULL AND purchase_item_id IS NULL)
    )
);

CREATE UNIQUE INDEX inventory_movements_business_id_id_uq ON inventory_movements (business_id, id);
CREATE INDEX inventory_movements_business_product_created_idx ON inventory_movements (business_id, product_id, created_at);

CREATE TABLE sale_payments (
    id uuid PRIMARY KEY,
    business_id uuid NOT NULL,
    sale_id uuid NOT NULL,
    amount decimal(14,2) NOT NULL,
    payment_method payment_method NOT NULL,
    reference_number varchar(100),
    paid_at timestamptz DEFAULT now(),
    created_at timestamptz DEFAULT now()
);

CREATE TABLE purchase_payments (
    id uuid PRIMARY KEY,
    business_id uuid NOT NULL,
    purchase_id uuid NOT NULL,
    amount decimal(14,2) NOT NULL,
    payment_method payment_method NOT NULL,
    reference_number varchar(100),
    paid_at timestamptz DEFAULT now(),
    created_at timestamptz DEFAULT now()
);

ALTER TABLE memberships
    ADD CONSTRAINT memberships_business_id_fk FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE RESTRICT;
ALTER TABLE memberships
    ADD CONSTRAINT memberships_user_id_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT;

ALTER TABLE customers
    ADD CONSTRAINT customers_business_id_fk FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE RESTRICT;
ALTER TABLE suppliers
    ADD CONSTRAINT suppliers_business_id_fk FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE RESTRICT;
ALTER TABLE categories
    ADD CONSTRAINT categories_business_id_fk FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE RESTRICT;
ALTER TABLE products
    ADD CONSTRAINT products_business_id_fk FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE RESTRICT;
ALTER TABLE products
    ADD CONSTRAINT products_category_fk FOREIGN KEY (business_id, category_id) REFERENCES categories (business_id, id) ON DELETE RESTRICT;

ALTER TABLE sales
    ADD CONSTRAINT sales_business_id_fk FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE RESTRICT;
ALTER TABLE sales
    ADD CONSTRAINT sales_customer_fk FOREIGN KEY (business_id, customer_id) REFERENCES customers (business_id, id) ON DELETE RESTRICT;
ALTER TABLE sales
    ADD CONSTRAINT sales_created_by_fk FOREIGN KEY (business_id, created_by_user_id) REFERENCES memberships (business_id, user_id) ON DELETE RESTRICT;
ALTER TABLE sale_items
    ADD CONSTRAINT sale_items_sale_fk FOREIGN KEY (business_id, sale_id) REFERENCES sales (business_id, id) ON DELETE CASCADE;
ALTER TABLE sale_items
    ADD CONSTRAINT sale_items_product_fk FOREIGN KEY (business_id, product_id) REFERENCES products (business_id, id) ON DELETE RESTRICT;

ALTER TABLE purchases
    ADD CONSTRAINT purchases_business_id_fk FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE RESTRICT;
ALTER TABLE purchases
    ADD CONSTRAINT purchases_supplier_fk FOREIGN KEY (business_id, supplier_id) REFERENCES suppliers (business_id, id) ON DELETE RESTRICT;
ALTER TABLE purchases
    ADD CONSTRAINT purchases_created_by_fk FOREIGN KEY (business_id, created_by_user_id) REFERENCES memberships (business_id, user_id) ON DELETE RESTRICT;
ALTER TABLE purchase_items
    ADD CONSTRAINT purchase_items_purchase_fk FOREIGN KEY (business_id, purchase_id) REFERENCES purchases (business_id, id) ON DELETE CASCADE;
ALTER TABLE purchase_items
    ADD CONSTRAINT purchase_items_product_fk FOREIGN KEY (business_id, product_id) REFERENCES products (business_id, id) ON DELETE RESTRICT;

ALTER TABLE inventory_movements
    ADD CONSTRAINT inventory_movements_product_fk FOREIGN KEY (business_id, product_id) REFERENCES products (business_id, id) ON DELETE RESTRICT;
ALTER TABLE inventory_movements
    ADD CONSTRAINT inventory_movements_sale_item_fk FOREIGN KEY (business_id, sale_item_id, product_id) REFERENCES sale_items (business_id, id, product_id) ON DELETE RESTRICT;
ALTER TABLE inventory_movements
    ADD CONSTRAINT inventory_movements_purchase_item_fk FOREIGN KEY (business_id, purchase_item_id, product_id) REFERENCES purchase_items (business_id, id, product_id) ON DELETE RESTRICT;
ALTER TABLE inventory_movements
    ADD CONSTRAINT inventory_movements_created_by_fk FOREIGN KEY (business_id, created_by_user_id) REFERENCES memberships (business_id, user_id) ON DELETE RESTRICT;

ALTER TABLE sale_payments
    ADD CONSTRAINT sale_payments_sale_fk FOREIGN KEY (business_id, sale_id) REFERENCES sales (business_id, id) ON DELETE RESTRICT;
ALTER TABLE purchase_payments
    ADD CONSTRAINT purchase_payments_purchase_fk FOREIGN KEY (business_id, purchase_id) REFERENCES purchases (business_id, id) ON DELETE RESTRICT;
