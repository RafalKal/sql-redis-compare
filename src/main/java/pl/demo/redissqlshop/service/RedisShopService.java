package pl.demo.redissqlshop.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.connection.RedisServerCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Service;

import pl.demo.redissqlshop.model.Product;

@Service
public class RedisShopService {

    private static final Duration SESSION_TTL = Duration.ofMinutes(30);
    private static final Duration CART_TTL = Duration.ofHours(24);
    private static final Duration PRODUCT_CACHE_TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate stringRedisTemplate;

    public RedisShopService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void flushDatabase() {
        stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
            RedisServerCommands commands = connection.serverCommands();
            if (commands != null) {
                commands.flushDb();
            }
            return null;
        });
    }

    public void createSession(long userId, String token) {
        stringRedisTemplate.opsForValue().set(RedisKeys.session(userId), token, SESSION_TTL);
    }

    public Optional<String> getSession(long userId) {
        return Optional.ofNullable(stringRedisTemplate.opsForValue().get(RedisKeys.session(userId)));
    }

    public void addProductToCart(long userId, long productId) {
        String cartKey = RedisKeys.cart(userId);
        stringRedisTemplate.opsForHash().increment(cartKey, RedisKeys.cartField(productId), 1L);
        stringRedisTemplate.expire(cartKey, CART_TTL);
    }

    public void storeCart(long userId, Map<Long, Integer> items) {
        String cartKey = RedisKeys.cart(userId);
        stringRedisTemplate.delete(cartKey);
        Map<String, String> values = new LinkedHashMap<String, String>();
        for (Map.Entry<Long, Integer> entry : items.entrySet()) {
            values.put(RedisKeys.cartField(entry.getKey()), String.valueOf(entry.getValue()));
        }
        if (!values.isEmpty()) {
            stringRedisTemplate.opsForHash().putAll(cartKey, values);
            stringRedisTemplate.expire(cartKey, CART_TTL);
        }
    }

    public Map<Long, Integer> getCart(long userId) {
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(RedisKeys.cart(userId));
        Map<Long, Integer> cart = new LinkedHashMap<Long, Integer>();
        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            String field = String.valueOf(entry.getKey());
            String value = String.valueOf(entry.getValue());
            long productId = Long.parseLong(field.replace("product:", ""));
            cart.put(productId, Integer.parseInt(value));
        }
        return cart;
    }

    public void cacheProduct(Product product) {
        String productKey = RedisKeys.cacheProduct(product.getId());
        Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("id", String.valueOf(product.getId()));
        values.put("sku", product.getSku());
        values.put("name", product.getName());
        values.put("category", product.getCategory());
        values.put("price", product.getPrice().toPlainString());
        values.put("stock", String.valueOf(product.getStock()));
        stringRedisTemplate.opsForHash().putAll(productKey, values);
        stringRedisTemplate.expire(productKey, PRODUCT_CACHE_TTL);
    }

    public Optional<Product> getCachedProduct(long productId) {
        Map<Object, Object> productData = stringRedisTemplate.opsForHash().entries(RedisKeys.cacheProduct(productId));
        if (productData.isEmpty()) {
            return Optional.empty();
        }
        Product product = new Product();
        product.setId(Long.parseLong(String.valueOf(productData.get("id"))));
        product.setSku(String.valueOf(productData.get("sku")));
        product.setName(String.valueOf(productData.get("name")));
        product.setCategory(String.valueOf(productData.get("category")));
        product.setPrice(new BigDecimal(String.valueOf(productData.get("price"))));
        product.setStock(Integer.parseInt(String.valueOf(productData.get("stock"))));
        return Optional.of(product);
    }

    public void incrementProductViews(long productId) {
        stringRedisTemplate.opsForValue().increment(RedisKeys.productViews(productId));
    }

    public void setProductViews(long productId, long viewsCount) {
        stringRedisTemplate.opsForValue().set(RedisKeys.productViews(productId), String.valueOf(viewsCount));
    }

    public void incrementBestsellerScore(long productId) {
        stringRedisTemplate.opsForZSet().incrementScore(RedisKeys.BESTSELLERS, String.valueOf(productId), 1.0d);
    }

    public void setBestsellerScore(long productId, long salesCount) {
        stringRedisTemplate.opsForZSet().add(RedisKeys.BESTSELLERS, String.valueOf(productId), salesCount);
    }

    public List<Map<String, Object>> getTopBestsellers(int limit) {
        Set<TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet()
            .reverseRangeWithScores(RedisKeys.BESTSELLERS, 0, limit - 1);
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        if (tuples == null) {
            return result;
        }
        for (TypedTuple<String> tuple : tuples) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("productId", Long.parseLong(tuple.getValue()));
            row.put("salesCount", tuple.getScore() == null ? 0L : tuple.getScore().longValue());
            result.add(row);
        }
        return result;
    }
}
