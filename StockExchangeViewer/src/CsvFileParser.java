import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class CsvFileParser {
    public Map<String, Double> parseFile(Path filePath) throws IOException {
        Map<String, Double> entryPairs = new TreeMap<>();
        List<String> lines = Files.readAllLines(filePath);

        // Die erste Zeile enthält die Spaltenüberschriften.
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }

            String[] parts = line.split(";", -1);
            if (parts.length < 2) {
                throw new IOException("Ungültiger Datensatz in Zeile " + (i + 1));
            }

            try {
                String date = LocalDate.parse(parts[0].trim()).toString();
                double value = Double.parseDouble(parts[1].trim().replace(',', '.'));
                if (!Double.isFinite(value)) {
                    throw new NumberFormatException("Kein endlicher Kurswert");
                }
                entryPairs.put(date, value);
            } catch (DateTimeParseException | NumberFormatException ex) {
                throw new IOException("Ungültiger Datensatz in Zeile " + (i + 1), ex);
            }
        }

        return entryPairs;
    }
}
