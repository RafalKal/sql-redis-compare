package pl.demo.redissqlshop.demo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import pl.demo.redissqlshop.model.Product;
import pl.demo.redissqlshop.model.User;
import pl.demo.redissqlshop.seed.DataSeeder;
import pl.demo.redissqlshop.service.RedisShopService;
import pl.demo.redissqlshop.service.SqlShopService;

@Component
public class DemoScenarioRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoScenarioRunner.class);

    private final DataSeeder dataSeeder;
    private final SqlShopService sqlShopService;
    private final RedisShopService redisShopService;

    public DemoScenarioRunner(
        DataSeeder dataSeeder,
        SqlShopService sqlShopService,
        RedisShopService redisShopService
    ) {
        this.dataSeeder = dataSeeder;
        this.sqlShopService = sqlShopService;
        this.redisShopService = redisShopService;
    }

    public Map<String, Object> runScenario() {
        dataSeeder.ensureSeedData();

        long userId = 1L;
        long productId = 10L;
        String sessionToken = "demo-session-token-" + userId;

        User user = sqlShopService.getUserById(userId)
            .orElseThrow(() -> new IllegalStateException("Brak użytkownika o ID = " + userId));
        Product product = sqlShopService.getProductById(productId)
            .orElseThrow(() -> new IllegalStateException("Brak produktu o ID = " + productId));

        log.info("DEMO: Redis w systemie sklepu internetowego");
        log.info("1. Pobrano użytkownika z SQL: {} {} ({})", user.getFirstName(), user.getLastName(), user.getEmail());

        sqlShopService.createOrUpdateUserSession(userId, sessionToken);
        redisShopService.createSession(userId, sessionToken);
        log.info("2. [SQL] Utworzono sesję w sql_user_sessions.");
        log.info("2. [Redis] Utworzono sesję użytkownika z TTL 1800 sekund.");

        log.info("3. [SQL] Pobrano produkt {}: {} za {}.", productId, product.getName(), product.getPrice());

        redisShopService.cacheProduct(product);
        log.info("4. [Redis] Zapisano produkt {} do cache:product:{}.", productId, productId);

        sqlShopService.addProductToCart(userId, productId);
        redisShopService.addProductToCart(userId, productId);
        log.info("5. [SQL] Dodano produkt {} do koszyka użytkownika {}.", productId, userId);
        log.info("5. [Redis] Dodano produkt {} do koszyka cart:{}.", productId, userId);

        Map<Long, Integer> sqlCart = sqlShopService.getCart(userId);
        Map<Long, Integer> redisCart = redisShopService.getCart(userId);
        log.info("6. [SQL] Odczyt koszyka: {}", sqlCart);
        log.info("6. [Redis] Odczyt koszyka: {}", redisCart);

        sqlShopService.incrementProductViews(productId);
        redisShopService.incrementProductViews(productId);
        log.info("7. [SQL] Zwiększono licznik wyświetleń produktu {}.", productId);
        log.info("7. [Redis] Zwiększono licznik views:product:{}.", productId);

        sqlShopService.incrementProductSales(productId);
        redisShopService.incrementBestsellerScore(productId);
        log.info("8. [SQL] Zwiększono sprzedaż produktu {}.", productId);
        log.info("8. [Redis] Zaktualizowano ranking bestsellers.");

        List<Map<String, Object>> sqlBestsellers = sqlShopService.getTopBestsellers(10);
        List<Map<String, Object>> redisBestsellers = redisShopService.getTopBestsellers(10);
        log.info("9. [SQL] TOP 10 bestsellerów: {}", sqlBestsellers);
        log.info("9. [Redis] TOP 10 bestsellerów: {}", redisBestsellers);

        Map<Long, Integer> orderItems = new LinkedHashMap<Long, Integer>();
        orderItems.put(productId, 1);
        Map<String, Object> orderSummary = sqlShopService.createOrderAndInvoice(userId, orderItems);
        log.info("10. [SQL] Utworzono zamówienie i fakturę: {}", orderSummary);

        String explanation = "PostgreSQL przechowuje dane trwałe i relacyjne. Redis obsługuje dane szybkie, " +
            "tymczasowe i często zmieniające się. Faktura nie jest zapisywana w Redis jako główne źródło danych, " +
            "bo wymaga trwałości, integralności i audytu.";
        log.info("11. Wniosek: {}", explanation);

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("user", user);
        result.put("product", product);
        result.put("sqlSession", sqlShopService.getActiveUserSession(userId).orElse(null));
        result.put("redisSession", redisShopService.getSession(userId).orElse(null));
        result.put("sqlCart", sqlCart);
        result.put("redisCart", redisCart);
        result.put("sqlBestsellers", sqlBestsellers);
        result.put("redisBestsellers", redisBestsellers);
        result.put("orderSummary", orderSummary);
        result.put("message", explanation);
        return result;
    }
}
