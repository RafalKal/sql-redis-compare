package pl.demo.redissqlshop.benchmark;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import pl.demo.redissqlshop.seed.DataSeeder;

@Component
public class BenchmarkRunner {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkRunner.class);

    private final DataSeeder dataSeeder;
    private final BenchmarkService benchmarkService;
    private final BenchmarkCsvWriter benchmarkCsvWriter;
    private final String resultFile;

    public BenchmarkRunner(
        DataSeeder dataSeeder,
        BenchmarkService benchmarkService,
        BenchmarkCsvWriter benchmarkCsvWriter,
        @Value("${benchmark.resultFile:build/reports/benchmark-results.csv}") String resultFile
    ) {
        this.dataSeeder = dataSeeder;
        this.benchmarkService = benchmarkService;
        this.benchmarkCsvWriter = benchmarkCsvWriter;
        this.resultFile = resultFile;
    }

    public Map<String, Object> runBenchmark() {
        dataSeeder.ensureSeedData();
        benchmarkService.prepareComparableData();

        List<BenchmarkResult> results = benchmarkService.runBenchmarks();
        Path csvPath = benchmarkCsvWriter.write(resultFile, results);

        printTable(results);
        printConclusions();
        log.info("Benchmark zapisano do pliku CSV: {}", csvPath.toAbsolutePath());

        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("resultFile", csvPath.toAbsolutePath().toString());
        response.put("results", results);
        return response;
    }

    private void printTable(List<BenchmarkResult> results) {
        String border = "+-------------------------------------+------------+------------+------------+---------+";
        System.out.println(border);
        System.out.printf(
            "| %-35s | %10s | %10s | %10s | %7s |%n",
            "Operacja",
            "Iteracje",
            "SQL [ms]",
            "Redis [ms]",
            "Różnica"
        );
        System.out.println(border);
        for (BenchmarkResult result : results) {
            System.out.printf(
                Locale.US,
                "| %-35s | %10d | %10.2f | %10.2f | %6.2fx |%n",
                result.getOperationLabel(),
                result.getIterations(),
                result.getSqlMillis(),
                result.getRedisMillis(),
                result.getRatio()
            );
        }
        System.out.println(border);
    }

    private void printConclusions() {
        System.out.println();
        System.out.println("WNIOSKI:");
        System.out.println();
        System.out.println("1. Redis jest szybszy w prostych operacjach typu key-value, counter, hash i sorted set, ponieważ większość operacji wykonuje w pamięci RAM.");
        System.out.println("2. PostgreSQL jest lepszy dla trwałych danych biznesowych, takich jak faktury, zamówienia i produkty, ponieważ zapewnia relacje, integralność, transakcje i trwałość danych.");
        System.out.println("3. Redis nie powinien całkowicie zastępować PostgreSQL w systemie sklepu internetowego. Najlepiej sprawdza się jako uzupełnienie bazy SQL.");
        System.out.println("4. W architekturze sklepu internetowego PostgreSQL powinien być głównym źródłem prawdy dla danych biznesowych, a Redis warstwą przyspieszającą obsługę danych tymczasowych i często odczytywanych.");
        System.out.println();
        System.out.println("Benchmark nie ma udowodnić, że Redis jest zawsze lepszy od PostgreSQL. Pokazuje koszt wybranych prostych operacji w tej konkretnej aplikacji demonstracyjnej.");
    }
}
