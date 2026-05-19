package pl.demo.redissqlshop.seed;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import pl.demo.redissqlshop.model.Invoice;
import pl.demo.redissqlshop.model.Order;
import pl.demo.redissqlshop.model.OrderItem;
import pl.demo.redissqlshop.model.Product;
import pl.demo.redissqlshop.model.User;
import pl.demo.redissqlshop.service.RedisShopService;
import pl.demo.redissqlshop.service.SqlShopService;

@Component
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private static final int USER_COUNT = 1000;
    private static final int PRODUCT_COUNT = 1000;
    private static final int ORDER_COUNT = 200;
    private static final int ORDER_ITEM_COUNT = 600;
    private static final int INVOICE_COUNT = 200;
    private static final int SESSION_COUNT = 500;
    private static final int CART_COUNT = 500;
    private static final int PRODUCT_VIEW_COUNT = 1000;
    private static final int SALES_STATS_COUNT = 1000;

    private static final String[] CATEGORIES = {"Elektronika", "Dom", "Sport", "Książki", "Moda", "Gry"};
    private static final String[] SAMPLE_PRODUCT_NAMES = {
        "Laptop Lenovo",
        "Smartfon Samsung",
        "Słuchawki Sony",
        "Monitor Dell",
        "Klawiatura Logitech"
    };

    private final JdbcTemplate jdbcTemplate;
    private final RedisShopService redisShopService;
    private final SqlShopService sqlShopService;
    private final Clock clock;

    public DataSeeder(
        JdbcTemplate jdbcTemplate,
        RedisShopService redisShopService,
        SqlShopService sqlShopService,
        Clock clock
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisShopService = redisShopService;
        this.sqlShopService = sqlShopService;
        this.clock = clock;
    }

    @Transactional
    public Map<String, Object> seed() {
        log.info("SEED: czyszczenie PostgreSQL i Redis.");
        clearSqlTables();
        redisShopService.flushDatabase();

        Random random = new Random(12345L);
        LocalDateTime now = LocalDateTime.now(clock);

        List<User> users = buildUsers();
        List<Product> products = buildProducts(random);
        insertUsers(users);
        insertProducts(products);
        seedSessions(now);
        seedCarts(random);
        seedProductViews(random);
        seedSalesStats(random);
        seedOrdersAndInvoices(random, products, now.minusDays(30));
        resetSequences();

        Map<String, Object> summary = new LinkedHashMap<String, Object>();
        summary.put("users", USER_COUNT);
        summary.put("products", PRODUCT_COUNT);
        summary.put("orders", ORDER_COUNT);
        summary.put("orderItems", ORDER_ITEM_COUNT);
        summary.put("invoices", INVOICE_COUNT);
        summary.put("activeSessions", SESSION_COUNT);
        summary.put("activeCarts", CART_COUNT);
        summary.put("productViews", PRODUCT_VIEW_COUNT);
        summary.put("salesStats", SALES_STATS_COUNT);

        log.info("SEED: zakończono zasilanie danych testowych: {}", summary);
        return summary;
    }

    public void ensureSeedData() {
        if (sqlShopService.countUsers() >= USER_COUNT && sqlShopService.countProducts() >= PRODUCT_COUNT) {
            return;
        }
        log.info("Brak pełnych danych testowych. Uruchamiam seed.");
        seed();
    }

    private void clearSqlTables() {
        jdbcTemplate.execute(
            "TRUNCATE TABLE invoices, order_items, orders, sql_cart_items, sql_user_sessions, " +
                "sql_product_views, sql_product_sales_stats, products, users RESTART IDENTITY CASCADE"
        );
    }

    private List<User> buildUsers() {
        List<User> users = new ArrayList<User>();
        for (long i = 1; i <= USER_COUNT; i++) {
            users.add(new User(i, "User" + i, "Test" + i, "user" + i + "@example.com"));
        }
        return users;
    }

    private List<Product> buildProducts(Random random) {
        List<Product> products = new ArrayList<Product>();
        for (long i = 1; i <= PRODUCT_COUNT; i++) {
            String name = i <= SAMPLE_PRODUCT_NAMES.length
                ? SAMPLE_PRODUCT_NAMES[(int) i - 1]
                : CATEGORIES[(int) ((i - 1) % CATEGORIES.length)] + " Produkt " + i;
            String category = CATEGORIES[(int) ((i - 1) % CATEGORIES.length)];
            BigDecimal price = BigDecimal.valueOf(50 + random.nextInt(5000) + random.nextDouble())
                .setScale(2, RoundingMode.HALF_UP);
            int stock = 5 + random.nextInt(95);
            products.add(new Product(i, String.format("SKU-%05d", i), name, category, price, stock));
        }
        return products;
    }

    private void insertUsers(List<User> users) {
        List<Object[]> args = new ArrayList<Object[]>();
        for (User user : users) {
            args.add(new Object[]{user.getId(), user.getFirstName(), user.getLastName(), user.getEmail()});
        }
        jdbcTemplate.batchUpdate("INSERT INTO users (id, first_name, last_name, email) VALUES (?, ?, ?, ?)", args);
    }

    private void insertProducts(List<Product> products) {
        List<Object[]> args = new ArrayList<Object[]>();
        for (Product product : products) {
            args.add(new Object[]{
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getCategory(),
                product.getPrice(),
                product.getStock()
            });
        }
        jdbcTemplate.batchUpdate(
            "INSERT INTO products (id, sku, name, category, price, stock) VALUES (?, ?, ?, ?, ?, ?)",
            args
        );
        for (Product product : products) {
            redisShopService.cacheProduct(product);
        }
    }

    private void seedSessions(LocalDateTime now) {
        List<Object[]> args = new ArrayList<Object[]>();
        for (long userId = 1; userId <= SESSION_COUNT; userId++) {
            String token = "session-token-" + userId;
            LocalDateTime createdAt = now.minusMinutes(userId % 15);
            LocalDateTime expiresAt = createdAt.plusMinutes(30);
            args.add(new Object[]{
                userId,
                token,
                Timestamp.valueOf(createdAt),
                Timestamp.valueOf(expiresAt)
            });
            redisShopService.createSession(userId, token);
        }
        jdbcTemplate.batchUpdate(
            "INSERT INTO sql_user_sessions (user_id, session_token, created_at, expires_at) VALUES (?, ?, ?, ?)",
            args
        );
    }

    private void seedCarts(Random random) {
        List<Object[]> sqlArgs = new ArrayList<Object[]>();
        for (long userId = 1; userId <= CART_COUNT; userId++) {
            Map<Long, Integer> cart = new LinkedHashMap<Long, Integer>();
            while (cart.size() < 3) {
                long productId = 1 + random.nextInt(PRODUCT_COUNT);
                int quantity = 1 + random.nextInt(4);
                cart.put(productId, quantity);
            }
            for (Map.Entry<Long, Integer> entry : cart.entrySet()) {
                sqlArgs.add(new Object[]{userId, entry.getKey(), entry.getValue()});
            }
            redisShopService.storeCart(userId, cart);
        }
        jdbcTemplate.batchUpdate(
            "INSERT INTO sql_cart_items (user_id, product_id, quantity) VALUES (?, ?, ?)",
            sqlArgs
        );
    }

    private void seedProductViews(Random random) {
        List<Object[]> args = new ArrayList<Object[]>();
        for (long productId = 1; productId <= PRODUCT_VIEW_COUNT; productId++) {
            long viewsCount = 10 + random.nextInt(5000);
            args.add(new Object[]{productId, viewsCount});
            redisShopService.setProductViews(productId, viewsCount);
        }
        jdbcTemplate.batchUpdate("INSERT INTO sql_product_views (product_id, views_count) VALUES (?, ?)", args);
    }

    private void seedSalesStats(Random random) {
        List<Object[]> args = new ArrayList<Object[]>();
        for (long productId = 1; productId <= SALES_STATS_COUNT; productId++) {
            long salesCount = 1 + random.nextInt(1000);
            args.add(new Object[]{productId, salesCount});
            redisShopService.setBestsellerScore(productId, salesCount);
        }
        jdbcTemplate.batchUpdate("INSERT INTO sql_product_sales_stats (product_id, sales_count) VALUES (?, ?)", args);
    }

    private void seedOrdersAndInvoices(Random random, List<Product> products, LocalDateTime baseTime) {
        List<Order> orders = new ArrayList<Order>();
        List<OrderItem> orderItems = new ArrayList<OrderItem>();
        List<Invoice> invoices = new ArrayList<Invoice>();
        long orderItemId = 1L;

        for (long orderId = 1; orderId <= ORDER_COUNT; orderId++) {
            long userId = 1 + random.nextInt(USER_COUNT);
            LocalDateTime createdAt = baseTime.plusHours(orderId);
            BigDecimal totalPrice = BigDecimal.ZERO;

            for (int itemIndex = 0; itemIndex < 3; itemIndex++) {
                Product product = products.get(random.nextInt(products.size()));
                int quantity = 1 + random.nextInt(3);
                orderItems.add(new OrderItem(orderItemId++, orderId, product.getId(), quantity, product.getPrice()));
                totalPrice = totalPrice.add(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
            }

            totalPrice = totalPrice.setScale(2, RoundingMode.HALF_UP);
            orders.add(new Order(orderId, userId, createdAt, totalPrice));
            invoices.add(new Invoice(
                orderId,
                orderId,
                String.format("INV-SEED-%05d", orderId),
                createdAt.plusMinutes(20),
                totalPrice
            ));
        }

        List<Object[]> orderArgs = new ArrayList<Object[]>();
        for (Order order : orders) {
            orderArgs.add(new Object[]{
                order.getId(),
                order.getUserId(),
                Timestamp.valueOf(order.getCreatedAt()),
                order.getTotalPrice()
            });
        }
        jdbcTemplate.batchUpdate("INSERT INTO orders (id, user_id, created_at, total_price) VALUES (?, ?, ?, ?)", orderArgs);

        List<Object[]> itemArgs = new ArrayList<Object[]>();
        for (OrderItem item : orderItems) {
            itemArgs.add(new Object[]{
                item.getId(),
                item.getOrderId(),
                item.getProductId(),
                item.getQuantity(),
                item.getUnitPrice()
            });
        }
        jdbcTemplate.batchUpdate(
            "INSERT INTO order_items (id, order_id, product_id, quantity, unit_price) VALUES (?, ?, ?, ?, ?)",
            itemArgs
        );

        List<Object[]> invoiceArgs = new ArrayList<Object[]>();
        for (Invoice invoice : invoices) {
            invoiceArgs.add(new Object[]{
                invoice.getId(),
                invoice.getOrderId(),
                invoice.getInvoiceNumber(),
                Timestamp.valueOf(invoice.getIssuedAt()),
                invoice.getTotalPrice()
            });
        }
        jdbcTemplate.batchUpdate(
            "INSERT INTO invoices (id, order_id, invoice_number, issued_at, total_price) VALUES (?, ?, ?, ?, ?)",
            invoiceArgs
        );
    }

    private void resetSequences() {
        resetSequence("users_id_seq", "users");
        resetSequence("products_id_seq", "products");
        resetSequence("orders_id_seq", "orders");
        resetSequence("order_items_id_seq", "order_items");
        resetSequence("invoices_id_seq", "invoices");
    }

    private void resetSequence(String sequenceName, String tableName) {
        jdbcTemplate.execute(
            "SELECT setval('" + sequenceName + "', COALESCE((SELECT MAX(id) FROM " + tableName + "), 1), true)"
        );
    }
}
