/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author info
 */
public class SearchResultFormatterHelper {
    public static String formatSearchResult(SearchResult result) {
        if (result.searchTermFound()) {
            return "'" + result.searchTerm() + "' an Position " + result.caretPosition() + " gefunden.";
        } else
            return "'" + result.searchTerm() + "' wurde nicht gefunden.";
    }
}
