package pl.demo.redissqlshop.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import pl.demo.redissqlshop.benchmark.BenchmarkRunner;
import pl.demo.redissqlshop.demo.DemoScenarioRunner;
import pl.demo.redissqlshop.model.Product;
import pl.demo.redissqlshop.seed.DataSeeder;
import pl.demo.redissqlshop.service.RedisShopService;
import pl.demo.redissqlshop.service.SqlShopService;

@RestController
@RequestMapping("/api/demo")
public class DemoController {

    private final DataSeeder dataSeeder;
    private final DemoScenarioRunner demoScenarioRunner;
    private final BenchmarkRunner benchmarkRunner;
    private final SqlShopService sqlShopService;
    private final RedisShopService redisShopService;

    public DemoController(
        DataSeeder dataSeeder,
        DemoScenarioRunner demoScenarioRunner,
        BenchmarkRunner benchmarkRunner,
        SqlShopService sqlShopService,
        RedisShopService redisShopService
    ) {
        this.dataSeeder = dataSeeder;
        this.demoScenarioRunner = demoScenarioRunner;
        this.benchmarkRunner = benchmarkRunner;
        this.sqlShopService = sqlShopService;
        this.redisShopService = redisShopService;
    }

    @PostMapping("/seed")
    public Map<String, Object> seed() {
        return dataSeeder.seed();
    }

    @PostMapping("/scenario")
    public Map<String, Object> scenario() {
        return demoScenarioRunner.runScenario();
    }

    @GetMapping("/sql/product/{id}")
    public Product getSqlProduct(@PathVariable long id) {
        return sqlShopService.getProductById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produkt nie istnieje w PostgreSQL."));
    }

    @GetMapping("/redis/product/{id}")
    public Product getRedisProduct(@PathVariable long id) {
        return redisShopService.getCachedProduct(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produkt nie istnieje w cache Redis."));
    }

    @PostMapping("/sql/cart/{userId}/product/{productId}")
    public ResponseEntity<Map<String, Object>> addSqlCartProduct(@PathVariable long userId, @PathVariable long productId) {
        sqlShopService.addProductToCart(userId, productId);
        return ResponseEntity.ok(cartResponse("sql", userId, sqlShopService.getCart(userId)));
    }

    @PostMapping("/redis/cart/{userId}/product/{productId}")
    public ResponseEntity<Map<String, Object>> addRedisCartProduct(@PathVariable long userId, @PathVariable long productId) {
        redisShopService.addProductToCart(userId, productId);
        return ResponseEntity.ok(cartResponse("redis", userId, redisShopService.getCart(userId)));
    }

    @GetMapping("/sql/cart/{userId}")
    public Map<Long, Integer> getSqlCart(@PathVariable long userId) {
        return sqlShopService.getCart(userId);
    }

    @GetMapping("/redis/cart/{userId}")
    public Map<Long, Integer> getRedisCart(@PathVariable long userId) {
        return redisShopService.getCart(userId);
    }

    @PostMapping("/benchmark")
    public Map<String, Object> benchmark() {
        return benchmarkRunner.runBenchmark();
    }

    @GetMapping("/bestsellers/sql")
    public Object getSqlBestsellers() {
        return sqlShopService.getTopBestsellers(10);
    }

    @GetMapping("/bestsellers/redis")
    public Object getRedisBestsellers() {
        return redisShopService.getTopBestsellers(10);
    }

    private Map<String, Object> cartResponse(String source, long userId, Map<Long, Integer> cart) {
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("source", source);
        response.put("userId", userId);
        response.put("cart", cart);
        return response;
    }
}
