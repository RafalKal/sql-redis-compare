package pl.demo.redissqlshop.service;

public final class RedisKeys {

    public static final String BESTSELLERS = "bestsellers";

    private RedisKeys() {
    }

    public static String session(long userId) {
        return "session:" + userId;
    }

    public static String cart(long userId) {
        return "cart:" + userId;
    }

    public static String cartField(long productId) {
        return "product:" + productId;
    }

    public static String cacheProduct(long productId) {
        return "cache:product:" + productId;
    }

    public static String productViews(long productId) {
        return "views:product:" + productId;
    }
}
