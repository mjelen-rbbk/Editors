
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author info
 */
public class CsvFileParser {
    public Map<String, Double> parseFile(Path filePath) throws IOException {
        Map<String, Double> entryPairs = Files.readAllLines(filePath)
        .stream()
        .skip(1)
        .filter(line -> !line.isBlank())
        .map(line -> line.split(";"))
        .filter(parts -> parts.length >= 2)
        .collect(Collectors.toMap(
                parts -> parts[0].trim(),
                parts -> Double.valueOf(parts[1].trim().replace(",", ".")),
                (existingValue, newValue) -> newValue
        ));
        
        return entryPairs;
    }
}
