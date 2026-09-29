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
            return "'" + result.replaceText()+ "' an Position " + result.replacePosition()+ " ersetzt.";
        } else
            return "'" + result.replaceText() + "' wurde nicht ersetzt.";
    }
}
