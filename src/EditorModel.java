
import java.util.Locale;
import javax.swing.JTextArea;

/**
 * Fachlogik fuer einen Texteditor. Unabhaengig von GUI und Controller. Nicht
 * ganz sauber da von JTextArea abgeleitet. Das stoert aber nicht, da Swing in
 * java SE enthalten ist.
 *
 * @author rla
 */
public class EditorModel extends JTextArea {
    private int findPosition = -1;

    public int getFindPosition() {
        return findPosition;
    }

    public SearchResult find(String searchString, boolean ignoreCase) {
        String normalizedSearchString;
        String normalizedText;
        if (ignoreCase) {
            normalizedSearchString = searchString.toLowerCase(Locale.ROOT);
            normalizedText = this.getText().toLowerCase(Locale.ROOT);
        } else {
            normalizedSearchString = searchString;
            normalizedText = this.getText();
        }

        int start = this.getCaretPosition();
        findPosition = normalizedText.indexOf(normalizedSearchString, start); 
        if (findPosition < 0) {
            return new SearchResult(findPosition, searchString, false);
        } else {
            this.setSelectionStart(findPosition);
            this.setSelectionEnd(findPosition + normalizedSearchString.length());
            
            return new SearchResult(findPosition, searchString, true);
        }
    }
}
