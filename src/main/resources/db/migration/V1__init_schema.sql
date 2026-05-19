CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(200) NOT NULL UNIQUE
);

CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    sku VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    category VARCHAR(100) NOT NULL,
    price NUMERIC(12, 2) NOT NULL,
    stock INTEGER NOT NULL
);

CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL,
    total_price NUMERIC(12, 2) NOT NULL
);

CREATE TABLE order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    product_id BIGINT NOT NULL REFERENCES products(id),
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL
);

CREATE TABLE invoices (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    invoice_number VARCHAR(100) NOT NULL UNIQUE,
    issued_at TIMESTAMP NOT NULL,
    total_price NUMERIC(12, 2) NOT NULL
);

CREATE TABLE sql_user_sessions (
    user_id BIGINT PRIMARY KEY REFERENCES users(id),
    session_token VARCHAR(200) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL
);

CREATE TABLE sql_cart_items (
    user_id BIGINT NOT NULL REFERENCES users(id),
    product_id BIGINT NOT NULL REFERENCES products(id),
    quantity INTEGER NOT NULL,
    PRIMARY KEY (user_id, product_id)
);

CREATE TABLE sql_product_views (
    product_id BIGINT PRIMARY KEY REFERENCES products(id),
    views_count BIGINT NOT NULL
);

CREATE TABLE sql_product_sales_stats (
    product_id BIGINT PRIMARY KEY REFERENCES products(id),
    sales_count BIGINT NOT NULL
);

CREATE INDEX idx_products_category ON products(category);
CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_sql_cart_items_user_id ON sql_cart_items(user_id);
CREATE INDEX idx_sql_product_sales_stats_sales_count ON sql_product_sales_stats(sales_count DESC);
