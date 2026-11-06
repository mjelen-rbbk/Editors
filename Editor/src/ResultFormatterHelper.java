/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author info
 */
public class ResultFormatterHelper {
    public static String formatSearchResult(SearchResult result) {
        if (result.searchTermFound()) {
            return "'" + result.searchTerm() + "' an Position " + result.findPosition() + " gefunden.";
        } else
            return "'" + result.searchTerm() + "' wurde nicht gefunden.";
    }
    
    public static String formatReplaceResult(ReplaceResult result) {
        if (result.textReplaced()) {
            return "Treffer an Position " + result.replacePosition()
                    + " durch '" + result.replaceText() + "' ersetzt.";
        } else
            return "'" + result.searchTerm() + "' wurde nicht gefunden und nicht ersetzt.";
    }

    public static String formatReplaceAllResult(int replacementCount) {
        if (replacementCount == 1) {
            return "1 Treffer wurde ersetzt.";
        }
        return replacementCount + " Treffer wurden ersetzt.";
    }
}
