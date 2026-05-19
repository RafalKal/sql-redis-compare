package pl.demo.redissqlshop.benchmark;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

@Component
public class BenchmarkCsvWriter {

    public Path write(String resultFile, List<BenchmarkResult> results) {
        Path path = Paths.get(resultFile);
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            List<String> lines = new ArrayList<String>();
            lines.add("operation,iterations,sql_ms,redis_ms,ratio");
            for (BenchmarkResult result : results) {
                lines.add(String.format(
                    Locale.US,
                    "%s,%d,%.2f,%.2f,%.2f",
                    result.getOperationKey(),
                    result.getIterations(),
                    result.getSqlMillis(),
                    result.getRedisMillis(),
                    result.getRatio()
                ));
            }
            Files.write(path, lines, StandardCharsets.UTF_8);
            return path;
        } catch (IOException ex) {
            throw new IllegalStateException("Nie udało się zapisać pliku CSV z wynikami benchmarku.", ex);
        }
    }
}
