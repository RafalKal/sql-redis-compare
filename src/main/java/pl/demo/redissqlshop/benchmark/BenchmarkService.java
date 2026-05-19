package pl.demo.redissqlshop.benchmark;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import pl.demo.redissqlshop.model.Product;
import pl.demo.redissqlshop.service.RedisShopService;
import pl.demo.redissqlshop.service.SqlShopService;

@Service
public class BenchmarkService {

    private static final int REPETITIONS = 5;
    private static final int PRODUCT_COUNT = 1000;
    private static final int CART_USER_COUNT = 500;

    private final SqlShopService sqlShopService;
    private final RedisShopService redisShopService;
    private final int iterations;
    private final int warmupIterations;

    public BenchmarkService(
        SqlShopService sqlShopService,
        RedisShopService redisShopService,
        @Value("${benchmark.iterations:10000}") int iterations,
        @Value("${benchmark.warmupIterations:1000}") int warmupIterations
    ) {
        this.sqlShopService = sqlShopService;
        this.redisShopService = redisShopService;
        this.iterations = iterations;
        this.warmupIterations = warmupIterations;
    }

    public void prepareComparableData() {
        for (Product product : sqlShopService.getAllProducts()) {
            redisShopService.cacheProduct(product);
        }
        for (long userId = 1; userId <= CART_USER_COUNT; userId++) {
            Map<Long, Integer> sqlCart = sqlShopService.getCart(userId);
            if (!sqlCart.isEmpty()) {
                redisShopService.storeCart(userId, sqlCart);
            }
        }
    }

    public List<BenchmarkResult> runBenchmarks() {
        List<BenchmarkResult> results = new ArrayList<BenchmarkResult>();
        results.add(runBenchmark(
            "read_product",
            "Odczyt produktu",
            buildReadProductSqlOperation(),
            buildReadProductRedisOperation()
        ));
        results.add(runBenchmark(
            "create_session",
            "Utworzenie sesji",
            buildCreateSessionSqlOperation(),
            buildCreateSessionRedisOperation()
        ));
        results.add(runBenchmark(
            "add_to_cart",
            "Dodanie produktu do koszyka",
            buildAddToCartSqlOperation(),
            buildAddToCartRedisOperation()
        ));
        results.add(runBenchmark(
            "read_cart",
            "Odczyt koszyka",
            buildReadCartSqlOperation(),
            buildReadCartRedisOperation()
        ));
        results.add(runBenchmark(
            "increment_views",
            "Licznik wyświetleń",
            buildIncrementViewsSqlOperation(),
            buildIncrementViewsRedisOperation()
        ));
        results.add(runBenchmark(
            "update_bestsellers",
            "Aktualizacja rankingu bestsellerów",
            buildUpdateBestsellersSqlOperation(),
            buildUpdateBestsellersRedisOperation()
        ));
        results.add(runBenchmark(
            "top_bestsellers",
            "TOP 10 bestsellerów",
            buildTopBestsellersSqlOperation(),
            buildTopBestsellersRedisOperation()
        ));
        return results;
    }

    private BenchmarkResult runBenchmark(
        String operationKey,
        String operationLabel,
        Runnable sqlOperation,
        Runnable redisOperation
    ) {
        warmup(sqlOperation);
        warmup(redisOperation);

        double sqlMillis = averageExecutionTimeMillis(sqlOperation);
        double redisMillis = averageExecutionTimeMillis(redisOperation);
        double ratio = redisMillis == 0.0d ? 0.0d : sqlMillis / redisMillis;

        return new BenchmarkResult(operationKey, operationLabel, iterations, sqlMillis, redisMillis, ratio);
    }

    private void warmup(Runnable operation) {
        for (int i = 0; i < warmupIterations; i++) {
            operation.run();
        }
    }

    private double averageExecutionTimeMillis(Runnable operation) {
        double totalMillis = 0.0d;
        for (int repeat = 0; repeat < REPETITIONS; repeat++) {
            long nanos = measureNanos(() -> execute(operation, iterations));
            totalMillis += nanos / 1_000_000.0d;
        }
        return totalMillis / REPETITIONS;
    }

    private void execute(Runnable operation, int count) {
        for (int i = 0; i < count; i++) {
            operation.run();
        }
    }

    private long measureNanos(Runnable operation) {
        long start = System.nanoTime();
        operation.run();
        return System.nanoTime() - start;
    }

    private Runnable buildReadProductSqlOperation() {
        AtomicInteger counter = new AtomicInteger();
        return () -> sqlShopService.getProductById(nextId(counter, PRODUCT_COUNT))
            .orElseThrow(() -> new IllegalStateException("Brak produktu do benchmarku SQL."));
    }

    private Runnable buildReadProductRedisOperation() {
        AtomicInteger counter = new AtomicInteger();
        return () -> redisShopService.getCachedProduct(nextId(counter, PRODUCT_COUNT))
            .orElseThrow(() -> new IllegalStateException("Brak produktu do benchmarku Redis."));
    }

    private Runnable buildCreateSessionSqlOperation() {
        AtomicInteger counter = new AtomicInteger();
        return () -> {
            int index = counter.getAndIncrement();
            long userId = 1 + (index % CART_USER_COUNT);
            sqlShopService.createOrUpdateUserSession(userId, "bench-sql-token-" + index);
        };
    }

    private Runnable buildCreateSessionRedisOperation() {
        AtomicInteger counter = new AtomicInteger();
        return () -> {
            int index = counter.getAndIncrement();
            long userId = 1 + (index % CART_USER_COUNT);
            redisShopService.createSession(userId, "bench-redis-token-" + index);
        };
    }

    private Runnable buildAddToCartSqlOperation() {
        AtomicInteger counter = new AtomicInteger();
        return () -> {
            int index = counter.getAndIncrement();
            long userId = 1 + (index % CART_USER_COUNT);
            long productId = 1 + ((index * 7) % PRODUCT_COUNT);
            sqlShopService.addProductToCart(userId, productId);
        };
    }

    private Runnable buildAddToCartRedisOperation() {
        AtomicInteger counter = new AtomicInteger();
        return () -> {
            int index = counter.getAndIncrement();
            long userId = 1 + (index % CART_USER_COUNT);
            long productId = 1 + ((index * 7) % PRODUCT_COUNT);
            redisShopService.addProductToCart(userId, productId);
        };
    }

    private Runnable buildReadCartSqlOperation() {
        AtomicInteger counter = new AtomicInteger();
        return () -> sqlShopService.getCart(nextId(counter, CART_USER_COUNT));
    }

    private Runnable buildReadCartRedisOperation() {
        AtomicInteger counter = new AtomicInteger();
        return () -> redisShopService.getCart(nextId(counter, CART_USER_COUNT));
    }

    private Runnable buildIncrementViewsSqlOperation() {
        AtomicInteger counter = new AtomicInteger();
        return () -> sqlShopService.incrementProductViews(nextId(counter, PRODUCT_COUNT));
    }

    private Runnable buildIncrementViewsRedisOperation() {
        AtomicInteger counter = new AtomicInteger();
        return () -> redisShopService.incrementProductViews(nextId(counter, PRODUCT_COUNT));
    }

    private Runnable buildUpdateBestsellersSqlOperation() {
        AtomicInteger counter = new AtomicInteger();
        return () -> sqlShopService.incrementProductSales(nextId(counter, PRODUCT_COUNT));
    }

    private Runnable buildUpdateBestsellersRedisOperation() {
        AtomicInteger counter = new AtomicInteger();
        return () -> redisShopService.incrementBestsellerScore(nextId(counter, PRODUCT_COUNT));
    }

    private Runnable buildTopBestsellersSqlOperation() {
        return () -> sqlShopService.getTopBestsellers(10);
    }

    private Runnable buildTopBestsellersRedisOperation() {
        return () -> redisShopService.getTopBestsellers(10);
    }

    private long nextId(AtomicInteger counter, int maxValue) {
        return 1L + Math.floorMod(counter.getAndIncrement(), maxValue);
    }
}
