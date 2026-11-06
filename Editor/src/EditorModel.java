
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
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
        if (searchString == null || searchString.isEmpty()) {
            findPosition = -1;
            return new SearchResult(findPosition, searchString, false);
        }

        int start = this.getCaretPosition();
        findPosition = findFrom(getText(), searchString, start, ignoreCase);
        if (findPosition < 0) {
            return new SearchResult(findPosition, searchString, false);
        } else {
            this.setSelectionStart(findPosition);
            this.setSelectionEnd(findPosition + searchString.length());
            
            return new SearchResult(findPosition, searchString, true);
        }
    }

    public ReplaceResult replace(String searchString, String replaceString, boolean ignoreCase) {
        Objects.requireNonNull(replaceString, "Der Ersatztext darf nicht null sein.");
        if (searchString == null || searchString.isEmpty()) {
            return new ReplaceResult(-1, searchString, replaceString, false);
        }

        if (!selectionMatches(searchString, ignoreCase)) {
            SearchResult searchResult = find(searchString, ignoreCase);
            if (!searchResult.searchTermFound()) {
                return new ReplaceResult(-1, searchString, replaceString, false);
            }
        }

        int replacePosition = getSelectionStart();
        replaceSelection(replaceString);
        findPosition = -1;
        return new ReplaceResult(replacePosition, searchString, replaceString, true);
    }

    public int replaceAll(String searchString, String replaceString, boolean ignoreCase) {
        Objects.requireNonNull(replaceString, "Der Ersatztext darf nicht null sein.");
        if (searchString == null || searchString.isEmpty()) {
            return 0;
        }

        String originalText = getText();
        int matchPosition = findFrom(originalText, searchString, 0, ignoreCase);
        if (matchPosition < 0) {
            return 0;
        }

        StringBuilder replacedText = new StringBuilder(originalText.length());
        int copyPosition = 0;
        int replacementCount = 0;
        while (matchPosition >= 0) {
            replacedText.append(originalText, copyPosition, matchPosition);
            replacedText.append(replaceString);
            copyPosition = matchPosition + searchString.length();
            replacementCount++;
            matchPosition = findFrom(originalText, searchString, copyPosition, ignoreCase);
        }
        replacedText.append(originalText, copyPosition, originalText.length());

        int oldCaretPosition = getCaretPosition();
        setText(replacedText.toString());
        setCaretPosition(Math.min(oldCaretPosition, getDocument().getLength()));
        findPosition = -1;
        return replacementCount;
    }

    private boolean selectionMatches(String searchString, boolean ignoreCase) {
        String selectedText = getSelectedText();
        return selectedText != null
                && selectedText.length() == searchString.length()
                && selectedText.regionMatches(ignoreCase, 0, searchString, 0, searchString.length());
    }

    private int findFrom(String text, String searchString, int start, boolean ignoreCase) {
        if (!ignoreCase) {
            return text.indexOf(searchString, start);
        }

        int lastStart = text.length() - searchString.length();
        for (int position = Math.max(0, start); position <= lastStart; position++) {
            if (text.regionMatches(true, position, searchString, 0, searchString.length())) {
                return position;
            }
        }
        return -1;
    }

    public record LoadResult(int characterCount, long byteCount) {
    }
}
