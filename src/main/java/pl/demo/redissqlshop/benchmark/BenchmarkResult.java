package pl.demo.redissqlshop.benchmark;

public class BenchmarkResult {

    private final String operationKey;
    private final String operationLabel;
    private final int iterations;
    private final double sqlMillis;
    private final double redisMillis;
    private final double ratio;

    public BenchmarkResult(
        String operationKey,
        String operationLabel,
        int iterations,
        double sqlMillis,
        double redisMillis,
        double ratio
    ) {
        this.operationKey = operationKey;
        this.operationLabel = operationLabel;
        this.iterations = iterations;
        this.sqlMillis = sqlMillis;
        this.redisMillis = redisMillis;
        this.ratio = ratio;
    }

    public String getOperationKey() {
        return operationKey;
    }

    public String getOperationLabel() {
        return operationLabel;
    }

    public int getIterations() {
        return iterations;
    }

    public double getSqlMillis() {
        return sqlMillis;
    }

    public double getRedisMillis() {
        return redisMillis;
    }

    public double getRatio() {
        return ratio;
    }
}
