package pl.demo.redissqlshop;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import pl.demo.redissqlshop.benchmark.BenchmarkRunner;
import pl.demo.redissqlshop.demo.DemoScenarioRunner;
import pl.demo.redissqlshop.seed.DataSeeder;

@SpringBootApplication
public class RedisSqlShopApplication implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RedisSqlShopApplication.class);

    private final DataSeeder dataSeeder;
    private final DemoScenarioRunner demoScenarioRunner;
    private final BenchmarkRunner benchmarkRunner;
    private final String appMode;

    public RedisSqlShopApplication(
        DataSeeder dataSeeder,
        DemoScenarioRunner demoScenarioRunner,
        BenchmarkRunner benchmarkRunner,
        @Value("${app.mode:demo}") String appMode
    ) {
        this.dataSeeder = dataSeeder;
        this.demoScenarioRunner = demoScenarioRunner;
        this.benchmarkRunner = benchmarkRunner;
        this.appMode = appMode;
    }

    public static void main(String[] args) {
        SpringApplication.run(RedisSqlShopApplication.class, args);
    }

    @Override
    public void run(String... args) {
        String mode = appMode == null ? "demo" : appMode.toLowerCase(Locale.ROOT);
        switch (mode) {
            case "seed":
                dataSeeder.seed();
                break;
            case "demo":
                demoScenarioRunner.runScenario();
                break;
            case "benchmark":
                benchmarkRunner.runBenchmark();
                break;
            default:
                log.warn("Nieznany tryb app.mode='{}'. Dostępne tryby: seed, demo, benchmark.", appMode);
        }
    }
}
