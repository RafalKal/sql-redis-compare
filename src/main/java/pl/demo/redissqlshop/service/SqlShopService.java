package pl.demo.redissqlshop.service;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pl.demo.redissqlshop.model.Product;
import pl.demo.redissqlshop.model.User;

@Service
public class SqlShopService {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final RowMapper<User> userRowMapper = (rs, rowNum) -> new User(
        rs.getLong("id"),
        rs.getString("first_name"),
        rs.getString("last_name"),
        rs.getString("email")
    );
    private final RowMapper<Product> productRowMapper = (rs, rowNum) -> new Product(
        rs.getLong("id"),
        rs.getString("sku"),
        rs.getString("name"),
        rs.getString("category"),
        rs.getBigDecimal("price"),
        rs.getInt("stock")
    );

    public SqlShopService(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    public int countUsers() {
        Integer result = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        return result == null ? 0 : result;
    }

    public int countProducts() {
        Integer result = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM products", Integer.class);
        return result == null ? 0 : result;
    }

    public Optional<User> getUserById(long userId) {
        return queryOptional("SELECT id, first_name, last_name, email FROM users WHERE id = ?", userRowMapper, userId);
    }

    public Optional<Product> getProductById(long productId) {
        return queryOptional(
            "SELECT id, sku, name, category, price, stock FROM products WHERE id = ?",
            productRowMapper,
            productId
        );
    }

    public List<Product> getAllProducts() {
        return jdbcTemplate.query(
            "SELECT id, sku, name, category, price, stock FROM products ORDER BY id",
            productRowMapper
        );
    }

    public void createOrUpdateUserSession(long userId, String token) {
        LocalDateTime createdAt = LocalDateTime.now(clock);
        LocalDateTime expiresAt = createdAt.plusMinutes(30);
        jdbcTemplate.update(
            "INSERT INTO sql_user_sessions (user_id, session_token, created_at, expires_at) " +
                "VALUES (?, ?, ?, ?) " +
                "ON CONFLICT (user_id) DO UPDATE SET " +
                "session_token = EXCLUDED.session_token, " +
                "created_at = EXCLUDED.created_at, " +
                "expires_at = EXCLUDED.expires_at",
            userId,
            token,
            Timestamp.valueOf(createdAt),
            Timestamp.valueOf(expiresAt)
        );
    }

    public Optional<String> getActiveUserSession(long userId) {
        try {
            String token = jdbcTemplate.queryForObject(
                "SELECT session_token FROM sql_user_sessions WHERE user_id = ? AND expires_at > ?",
                String.class,
                userId,
                Timestamp.valueOf(LocalDateTime.now(clock))
            );
            return Optional.ofNullable(token);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public void addProductToCart(long userId, long productId) {
        jdbcTemplate.update(
            "INSERT INTO sql_cart_items (user_id, product_id, quantity) VALUES (?, ?, 1) " +
                "ON CONFLICT (user_id, product_id) DO UPDATE SET quantity = sql_cart_items.quantity + 1",
            userId,
            productId
        );
    }

    public Map<Long, Integer> getCart(long userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT product_id, quantity FROM sql_cart_items WHERE user_id = ? ORDER BY product_id",
            userId
        );
        Map<Long, Integer> cart = new LinkedHashMap<Long, Integer>();
        for (Map<String, Object> row : rows) {
            cart.put(((Number) row.get("product_id")).longValue(), ((Number) row.get("quantity")).intValue());
        }
        return cart;
    }

    public void incrementProductViews(long productId) {
        jdbcTemplate.update(
            "INSERT INTO sql_product_views (product_id, views_count) VALUES (?, 1) " +
                "ON CONFLICT (product_id) DO UPDATE SET views_count = sql_product_views.views_count + 1",
            productId
        );
    }

    public void incrementProductSales(long productId) {
        jdbcTemplate.update(
            "INSERT INTO sql_product_sales_stats (product_id, sales_count) VALUES (?, 1) " +
                "ON CONFLICT (product_id) DO UPDATE SET sales_count = sql_product_sales_stats.sales_count + 1",
            productId
        );
    }

    public List<Map<String, Object>> getTopBestsellers(int limit) {
        return jdbcTemplate.query(
            "SELECT product_id, sales_count FROM sql_product_sales_stats ORDER BY sales_count DESC, product_id ASC LIMIT ?",
            ps -> ps.setInt(1, limit),
            (rs, rowNum) -> bestsellerRow(rs.getLong("product_id"), rs.getLong("sales_count"))
        );
    }

    @Transactional
    public Map<String, Object> createOrderAndInvoice(long userId, Map<Long, Integer> productQuantities) {
        if (productQuantities == null || productQuantities.isEmpty()) {
            throw new IllegalArgumentException("Lista produktów zamówienia nie może być pusta.");
        }
        if (!getUserById(userId).isPresent()) {
            throw new IllegalArgumentException("Użytkownik " + userId + " nie istnieje.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        Map<Product, Integer> resolvedItems = new LinkedHashMap<Product, Integer>();
        BigDecimal total = BigDecimal.ZERO;

        for (Map.Entry<Long, Integer> entry : productQuantities.entrySet()) {
            Product product = getProductById(entry.getKey())
                .orElseThrow(() -> new IllegalArgumentException("Produkt " + entry.getKey() + " nie istnieje."));
            int quantity = entry.getValue() == null ? 0 : entry.getValue().intValue();
            if (quantity <= 0) {
                throw new IllegalArgumentException("Ilość produktu musi być dodatnia.");
            }
            resolvedItems.put(product, quantity);
            total = total.add(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
        }
        final BigDecimal orderTotal = total;

        KeyHolder orderKeyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO orders (user_id, created_at, total_price) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, userId);
            statement.setTimestamp(2, Timestamp.valueOf(now));
            statement.setBigDecimal(3, orderTotal);
            return statement;
        }, orderKeyHolder);

        Map<String, Object> generatedKeys = orderKeyHolder.getKeys();
        Number orderNumber = generatedKeys == null ? orderKeyHolder.getKey() : (Number) generatedKeys.get("id");
        if (orderNumber == null) {
            throw new IllegalStateException("Nie udało się utworzyć zamówienia.");
        }
        long orderId = orderNumber.longValue();

        for (Map.Entry<Product, Integer> entry : resolvedItems.entrySet()) {
            jdbcTemplate.update(
                "INSERT INTO order_items (order_id, product_id, quantity, unit_price) VALUES (?, ?, ?, ?)",
                orderId,
                entry.getKey().getId(),
                entry.getValue(),
                entry.getKey().getPrice()
            );
        }

        // Zamówienia i faktury są zapisywane w PostgreSQL, ponieważ wymagają trwałości,
        // relacji, integralności i możliwości późniejszego audytu.
        String invoiceNumber = String.format("INV-%d-%06d", System.currentTimeMillis(), orderId);
        jdbcTemplate.update(
            "INSERT INTO invoices (order_id, invoice_number, issued_at, total_price) VALUES (?, ?, ?, ?)",
            orderId,
            invoiceNumber,
            Timestamp.valueOf(now),
            orderTotal
        );

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("orderId", orderId);
        result.put("userId", userId);
        result.put("invoiceNumber", invoiceNumber);
        result.put("totalPrice", orderTotal);
        result.put("itemCount", resolvedItems.size());
        return result;
    }

    private <T> Optional<T> queryOptional(String sql, RowMapper<T> mapper, Object... args) {
        List<T> result = jdbcTemplate.query(sql, mapper, args);
        if (result.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(result.get(0));
    }

    private Map<String, Object> bestsellerRow(long productId, long salesCount) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("productId", productId);
        row.put("salesCount", salesCount);
        return row;
    }
}
