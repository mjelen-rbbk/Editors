package blockwoche;

import org.junit.After;
import org.junit.Before;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public abstract class BaseTestCase {

    protected static final int WARMUP_RUNS = 25;
    protected static final int EXECUTIONS = 100;
    protected static final int REPETITIONS = 10;

    // 0.10 bedeutet: 10 % der kleinsten und 10 % der größten Messwerte werden
    // für den getrimmten Durchschnitt ignoriert.
    protected static final double TRIM_PERCENT = 0.10;

    private static final String RUN_TIMESTAMP =
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));

    private static final Path RESULT_DIR = Path.of("benchmark_ergebnisse", RUN_TIMESTAMP);
    private static final Path RAW_DIR = RESULT_DIR.resolve("rohwerte");
    private static final Path SUMMARY_CSV = RESULT_DIR.resolve("uebersicht.csv");

    private static boolean resultDirectoryPrinted = false;

    protected DataBase db;
    protected Connection connection;

    protected abstract String getDatabaseName();

    @Before
    public void setUp() throws SQLException, IOException {
        Files.createDirectories(RAW_DIR);

        db = new DataBase(getDatabaseName());
        connection = db.getConnection();

        if (connection == null) {
            throw new RuntimeException("Verbindung zur Datenbank fehlgeschlagen: " + getDatabaseName());
        }

        if (!resultDirectoryPrinted) {
            System.out.println("Benchmark-Ausgabeordner: " + RESULT_DIR.toAbsolutePath());
            resultDirectoryPrinted = true;
        }
    }

    @After
    public void tearDown() {
        if (db != null) {
            db.close();
        }
    }

    /**
     * Zentrale Benchmark-Methode.
     *
     * Gemessen wird die clientseitige Laufzeit:
     * Java -> JDBC -> PostgreSQL -> ResultSet zurück -> ResultSet vollständig lesen.
     */
    protected void runBenchmark(String queryLabel, String sql) {
        System.out.println("Starte Benchmark für [" + queryLabel + "] auf DB [" + getDatabaseName() + "]");

        Path rawCsv = RAW_DIR.resolve(safeFileName(queryLabel) + "_rohwerte.csv");

        try {
            runWarmUpPhase(queryLabel, sql);

            try (BufferedWriter rawWriter = openRawWriter(rawCsv)) {
                for (int rep = 1; rep <= REPETITIONS; rep++) {
                    List<Long> durationsNs = new ArrayList<>();

                    int successfulExecutions = 0;
                    int failedExecutions = 0;
                    long totalDurationNs = 0;
                    long totalRowsRead = 0;

                    try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                        for (int exec = 1; exec <= EXECUTIONS; exec++) {
                            long startTime = System.nanoTime();

                            try (ResultSet rs = pstmt.executeQuery()) {
                                long rowsRead = consumeResultSet(rs);
                                long durationNs = System.nanoTime() - startTime;

                                durationsNs.add(durationNs);
                                totalDurationNs += durationNs;
                                totalRowsRead += rowsRead;
                                successfulExecutions++;

                                writeRawLine(rawWriter, queryLabel, rep, exec, true, durationNs, rowsRead, "");

                            } catch (SQLException e) {
                                long durationNs = System.nanoTime() - startTime;

                                failedExecutions++;
                                writeRawLine(rawWriter, queryLabel, rep, exec, false, durationNs, 0, e.getMessage());

                                System.err.println("Fehler bei Exec " + exec
                                        + " in Repetition " + rep
                                        + " für [" + queryLabel + "]: "
                                        + e.getMessage());
                            }
                        }
                    }

                    rawWriter.flush();

                    if (successfulExecutions == 0) {
                        throw new RuntimeException("Keine erfolgreiche Ausführung für Query [" + queryLabel + "]");
                    }

                    Collections.sort(durationsNs);

                    int trimCount = calculateTrimCount(durationsNs.size());

                    double avgMs = nsToMs(totalDurationNs / (double) successfulExecutions);
                    double trimmedAvgMs = nsToMs(trimmedAverageNs(durationsNs, trimCount));
                    double minMs = nsToMs(durationsNs.get(0));
                    double p50Ms = nsToMs(percentile(durationsNs, 50));
                    double p90Ms = nsToMs(percentile(durationsNs, 90));
                    double p95Ms = nsToMs(percentile(durationsNs, 95));
                    double p99Ms = nsToMs(percentile(durationsNs, 99));
                    double maxMs = nsToMs(durationsNs.get(durationsNs.size() - 1));
                    double totalMs = nsToMs(totalDurationNs);
                    double avgRowsRead = totalRowsRead / (double) successfulExecutions;

                    System.out.printf(
                            Locale.ROOT,
                            "  -> Wiederholung %d/%d: Avg = %.4f ms, TrimmedAvg = %.4f ms, P50 = %.4f ms, P90 = %.4f ms, P95 = %.4f ms, P99 = %.4f ms, Fehler = %d%n",
                            rep,
                            REPETITIONS,
                            avgMs,
                            trimmedAvgMs,
                            p50Ms,
                            p90Ms,
                            p95Ms,
                            p99Ms,
                            failedExecutions
                    );

                    writeSummaryLine(
                            queryLabel,
                            rep,
                            successfulExecutions,
                            failedExecutions,
                            avgMs,
                            trimmedAvgMs,
                            trimCount,
                            minMs,
                            p50Ms,
                            p90Ms,
                            p95Ms,
                            p99Ms,
                            maxMs,
                            totalMs,
                            totalRowsRead,
                            avgRowsRead,
                            rawCsv
                    );
                }
            }

        } catch (IOException e) {
            throw new RuntimeException("Fehler beim Schreiben der CSV-Dateien", e);
        } catch (SQLException e) {
            throw new RuntimeException("Fehler beim Vorbereiten der SQL-Abfrage [" + queryLabel + "]", e);
        }
    }

    /**
     * Query-spezifisches WarmUp direkt vor der eigentlichen Messung.
     * Das ersetzt das alte WarmUp auf SELECT COUNT(*) FROM cities.
     */
    private void runWarmUpPhase(String queryLabel, String sql) throws SQLException {
        System.out.println("  -> WarmUp für [" + queryLabel + "] wird gestartet (" + WARMUP_RUNS + " Runs)");

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            for (int i = 1; i <= WARMUP_RUNS; i++) {
                try (ResultSet rs = pstmt.executeQuery()) {
                    consumeResultSet(rs);
                }
            }
        }

        System.out.println("  -> WarmUp für [" + queryLabel + "] beendet");
    }

    private long consumeResultSet(ResultSet rs) throws SQLException {
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();

        long rowsRead = 0;

        while (rs.next()) {
            for (int column = 1; column <= columnCount; column++) {
                rs.getObject(column);
            }

            rowsRead++;
        }

        return rowsRead;
    }

    private BufferedWriter openRawWriter(Path rawCsv) throws IOException {
        boolean writeHeader = Files.notExists(rawCsv) || Files.size(rawCsv) == 0;

        BufferedWriter writer = Files.newBufferedWriter(
                rawCsv,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );

        if (writeHeader) {
            writer.write("RunTimestamp,TestClass,DatabaseName,QueryLabel,RepetitionIndex,ExecutionIndex,Success,DurationNs,DurationMs,RowsRead,ErrorMessage");
            writer.newLine();
        }

        return writer;
    }

    private void writeRawLine(
            BufferedWriter writer,
            String queryLabel,
            int repetition,
            int execution,
            boolean success,
            long durationNs,
            long rowsRead,
            String errorMessage
    ) throws IOException {
        writer.write(String.join(",",
                csvEscape(RUN_TIMESTAMP),
                csvEscape(getClass().getSimpleName()),
                csvEscape(getDatabaseName()),
                csvEscape(queryLabel),
                String.valueOf(repetition),
                String.valueOf(execution),
                String.valueOf(success),
                String.valueOf(durationNs),
                formatDouble(nsToMs(durationNs)),
                String.valueOf(rowsRead),
                csvEscape(errorMessage)
        ));

        writer.newLine();
    }

    private synchronized void writeSummaryLine(
            String queryLabel,
            int repetition,
            int successfulExecutions,
            int failedExecutions,
            double avgMs,
            double trimmedAvgMs,
            int trimCount,
            double minMs,
            double p50Ms,
            double p90Ms,
            double p95Ms,
            double p99Ms,
            double maxMs,
            double totalMs,
            long totalRowsRead,
            double avgRowsRead,
            Path rawCsv
    ) throws IOException {
        boolean writeHeader = Files.notExists(SUMMARY_CSV) || Files.size(SUMMARY_CSV) == 0;

        try (BufferedWriter writer = Files.newBufferedWriter(
                SUMMARY_CSV,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        )) {
            if (writeHeader) {
                writer.write("RunTimestamp,TestClass,DatabaseName,QueryLabel,RepetitionIndex,ExecutionsConfigured,SuccessfulExecutions,FailedExecutions,AvgTimeMs,TrimmedAvgTimeMs,TrimPercent,DroppedLowCount,DroppedHighCount,MinTimeMs,P50TimeMs,P90TimeMs,P95TimeMs,P99TimeMs,MaxTimeMs,TotalTimeMs,TotalRowsRead,AvgRowsRead,RawCsvFile");
                writer.newLine();
            }

            writer.write(String.join(",",
                    csvEscape(RUN_TIMESTAMP),
                    csvEscape(getClass().getSimpleName()),
                    csvEscape(getDatabaseName()),
                    csvEscape(queryLabel),
                    String.valueOf(repetition),
                    String.valueOf(EXECUTIONS),
                    String.valueOf(successfulExecutions),
                    String.valueOf(failedExecutions),
                    formatDouble(avgMs),
                    formatDouble(trimmedAvgMs),
                    formatDouble(TRIM_PERCENT),
                    String.valueOf(trimCount),
                    String.valueOf(trimCount),
                    formatDouble(minMs),
                    formatDouble(p50Ms),
                    formatDouble(p90Ms),
                    formatDouble(p95Ms),
                    formatDouble(p99Ms),
                    formatDouble(maxMs),
                    formatDouble(totalMs),
                    String.valueOf(totalRowsRead),
                    formatDouble(avgRowsRead),
                    csvEscape(RESULT_DIR.relativize(rawCsv).toString())
            ));

            writer.newLine();
        }
    }

    /**
     * Nearest-rank percentile.
     *
     * Bei EXECUTIONS = 25 gilt:
     * P99 ist der 25. von 25 Werten, also praktisch MaxTimeMs.
     */
    private long percentile(List<Long> sortedValues, int percentile) {
        if (sortedValues.isEmpty()) {
            throw new IllegalArgumentException("sortedValues darf nicht leer sein");
        }

        int index = (int) Math.ceil((percentile / 100.0) * sortedValues.size()) - 1;
        index = Math.max(0, Math.min(index, sortedValues.size() - 1));

        return sortedValues.get(index);
    }

    private int calculateTrimCount(int valueCount) {
        int trimCount = (int) Math.floor(valueCount * TRIM_PERCENT);

        if (trimCount * 2 >= valueCount) {
            return 0;
        }

        return trimCount;
    }

    private double trimmedAverageNs(List<Long> sortedValues, int trimCount) {
        if (sortedValues.isEmpty()) {
            throw new IllegalArgumentException("sortedValues darf nicht leer sein");
        }

        int startIndex = trimCount;
        int endExclusive = sortedValues.size() - trimCount;

        long sum = 0;
        int count = 0;

        for (int i = startIndex; i < endExclusive; i++) {
            sum += sortedValues.get(i);
            count++;
        }

        return sum / (double) count;
    }

    private double nsToMs(long ns) {
        return ns / 1_000_000.0;
    }

    private double nsToMs(double ns) {
        return ns / 1_000_000.0;
    }

    private String formatDouble(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private String csvEscape(String value) {
        if (value == null) {
            return "";
        }

        boolean mustQuote = value.contains(",")
                || value.contains("\"")
                || value.contains("\n")
                || value.contains("\r");

        if (!mustQuote) {
            return value;
        }

        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private String safeFileName(String value) {
        if (value == null || value.isBlank()) {
            return "unbenannt";
        }

        return value
                .replaceAll("[^a-zA-Z0-9._-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+", "")
                .replaceAll("_+$", "");
    }

    protected String getQuery1() {
        return "SELECT * FROM cities ORDER BY name LIMIT 100;";
    }

    protected String getQuery2() {
        return "SELECT s.name, c.name " +
               "FROM stations s " +
               "JOIN cities c ON s.city_postal_code = c.postal_code " +
               "LIMIT 100;";
    }

    protected String getQuery3() {
        return "SELECT t.id, r.route_code, tr.id " +
               "FROM trips t " +
               "JOIN routes r ON t.route_id = r.id " +
               "JOIN trains tr ON t.train_id = tr.id " +
               "LIMIT 100;";
    }

    protected String getQuery4() {
        return "SELECT ttl.train_id, COUNT(*) AS telemetry_count " +
               "FROM train_telemetry_logs ttl " +
               "WHERE ttl.train_id IN ( " +
               "    SELECT t.id " +
               "    FROM trains t " +
               "    WHERE t.serial_number IN ('Tz 9001', 'Tz 9050', 'Tz 9100', 'Tz 9200', 'Tz 9300', 'Tz 9400') " +
               ") " +
               "GROUP BY ttl.train_id " +
               "ORDER BY telemetry_count DESC " +
               "LIMIT 50;";
    }

    protected String getQuery5() {
        return "SELECT ttl.train_id, r.route_code, e.first_name, e.last_name, COUNT(d.id) AS disruption_count " +
               "FROM train_telemetry_logs ttl " +
               "JOIN trips tr ON ttl.train_id = tr.train_id " +
               "JOIN routes r ON tr.route_id = r.id " +
               "JOIN trip_crews tc ON tr.id = tc.trip_id " +
               "JOIN employees e ON tc.employee_id = e.id " +
               "LEFT JOIN disruptions d ON tr.id = d.trip_id " +
               "GROUP BY ttl.train_id, r.route_code, e.first_name, e.last_name " +
               "LIMIT 10;";
    }
}