
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.JTextArea;

/**
 * Fachlogik fuer einen Texteditor. Unabhaengig von GUI und Controller. Nicht
 * ganz sauber da von JTextArea abgeleitet. Das stoert aber nicht, da Swing in
 * java SE enthalten ist.
 *
 * @author rla
 */
public class EditorModel extends JTextArea {
    public static final String DIRTY_PROPERTY = "dirty";

    private int findPosition = -1;
    private Path filePath;
    private boolean dirty;

    public EditorModel() {
        getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                markDirty();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                markDirty();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                markDirty();
            }
        });
    }

    public Path getFilePath() {
        return filePath;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void newDocument() {
        setText("");
        filePath = null;
        findPosition = -1;
        setCaretPosition(0);
        setDirty(false);
    }

    public LoadResult load(Path path) throws IOException {
        Path normalizedPath = normalize(path);
        String loadedText = Files.readString(normalizedPath, StandardCharsets.UTF_8);
        long byteCount = Files.size(normalizedPath);

        setText(loadedText);
        filePath = normalizedPath;
        findPosition = -1;
        setCaretPosition(getDocument().getLength());
        setDirty(false);

        return new LoadResult(loadedText.length(), byteCount);
    }

    public void save() throws IOException {
        if (filePath == null) {
            throw new IllegalStateException("Für das Dokument ist noch kein Dateipfad festgelegt.");
        }

        saveAs(filePath);
    }

    public void saveAs(Path path) throws IOException {
        Path normalizedPath = normalize(path);
        Files.writeString(normalizedPath, getText(), StandardCharsets.UTF_8);
        filePath = normalizedPath;
        setDirty(false);
    }

    private Path normalize(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("Der Dateipfad darf nicht null sein.");
        }

        return path.toAbsolutePath().normalize();
    }

    private void markDirty() {
        setDirty(true);
    }

    private void setDirty(boolean newDirty) {
        boolean oldDirty = dirty;
        dirty = newDirty;
        firePropertyChange(DIRTY_PROPERTY, oldDirty, newDirty);
    }

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
    
    public ReplaceResult replace(String replaceString) {
        if (this.getSelectedText() == null) {
            return new ReplaceResult(-1, replaceString, false);
        }
        
        int replacePosition = this.getSelectionStart();
        this.replaceSelection(replaceString);
        return new ReplaceResult(replacePosition, replaceString, true);
    }

    public record LoadResult(int characterCount, long byteCount) {
    }
}
