import javax.swing.JTextArea;

/**
 * Fachlogik fuer einen Texteditor. Unabhaengig von GUI und Controller. Nicht
 * ganz sauber da von JTextArea abgeleitet. Das stoert aber nicht, da Swing
 * in java SE enthalten ist.
 * @author rla
 */
public class EditorModel extends JTextArea {
	private String message = "";
	private int caretPosition = -1;

	public String getMessage() {
    	    return message; 
	}

	public int getCaretPosition() {
    	    return caretPosition;
	}

	public boolean find(String su) {
    	    String text = this.getText();
    	    int start = this.getCaretPosition();
    	    caretPosition = text.indexOf(su, start);
    	    if (caretPosition < 0) {
        	  message = "'" + su + "' nicht gefunden.";
        	  return false;
    	    } else {
        	  message = "'" + su + "' an Position " + caretPosition 
                  + " gefunden.";
        	this.setSelectionStart(caretPosition);
        	this.setSelectionEnd(caretPosition + su.length());
        	return true;
    	    }
	}
}