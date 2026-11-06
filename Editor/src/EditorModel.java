
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.BreakIterator;
import java.util.Locale;
import java.util.Objects;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.JTextArea;
import javax.swing.text.BadLocationException;

/**
 * Fachlogik fuer einen Texteditor. Unabhaengig von GUI und Controller. Nicht
 * ganz sauber da von JTextArea abgeleitet. Das stoert aber nicht, da Swing in
 * java SE enthalten ist.
 *
 * @author rla
 */
public class EditorModel extends JTextArea {
    public static final String DIRTY_PROPERTY = "dirty";
    private static final int GUIDE_COLUMN = 60;
    private static final String GUIDE_WIDTH_TEXT = "0".repeat(GUIDE_COLUMN);

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

    public int getCharacterCount() {
        return getDocument().getLength();
    }

    public int getWordCount() {
        return countWords(getText());
    }

    public static int countWords(String text) {
        BreakIterator words = BreakIterator.getWordInstance(Locale.GERMAN);
        words.setText(text);

        int wordCount = 0;
        int start = words.first();
        for (int end = words.next(); end != BreakIterator.DONE; start = end, end = words.next()) {
            if (containsLetterOrDigit(text, start, end)) {
                wordCount++;
            }
        }
        return wordCount;
    }

    public int insertAtCaret(String text) {
        Objects.requireNonNull(text, "Der einzufügende Text darf nicht null sein.");
        int insertPosition = getSelectionStart();
        replaceSelection(text);
        setCaretPosition(insertPosition + text.length());
        return insertPosition;
    }

    private static boolean containsLetterOrDigit(String text, int start, int end) {
        for (int index = start; index < end;) {
            int codePoint = text.codePointAt(index);
            if (Character.isLetterOrDigit(codePoint)) {
                return true;
            }
            index += Character.charCount(codePoint);
        }
        return false;
    }

    public void moveCaretToOffset(int offset) {
        int documentLength = getDocument().getLength();
        if (offset < 0 || offset > documentLength) {
            throw new IllegalArgumentException(
                    "Die Zeichenposition muss zwischen 0 und " + documentLength + " liegen."
            );
        }
        setCaretPosition(offset);
    }

    public void moveCaretToLine(int oneBasedLine) {
        int lineCount = getLineCount();
        if (oneBasedLine < 1 || oneBasedLine > lineCount) {
            throw new IllegalArgumentException(
                    "Die Zeile muss zwischen 1 und " + lineCount + " liegen."
            );
        }

        try {
            setCaretPosition(getLineStartOffset(oneBasedLine - 1));
        } catch (BadLocationException exception) {
            throw new IllegalStateException("Die Zeilenposition konnte nicht ermittelt werden.", exception);
        }
    }

    public void moveCaretToColumn(int zeroBasedColumn) {
        if (zeroBasedColumn < 0) {
            throw new IllegalArgumentException("Die Spalte darf nicht negativ sein.");
        }

        try {
            int line = getLineOfOffset(getCaretPosition());
            int lineStart = getLineStartOffset(line);
            int lineEnd = getLineEndOffset(line);
            if (lineEnd > lineStart && "\n".equals(getText(lineEnd - 1, 1))) {
                lineEnd--;
            }
            if (lineEnd > lineStart && "\r".equals(getText(lineEnd - 1, 1))) {
                lineEnd--;
            }

            int maximumColumn = lineEnd - lineStart;
            if (zeroBasedColumn > maximumColumn) {
                throw new IllegalArgumentException(
                        "Die Spalte muss in dieser Zeile zwischen 0 und "
                                + maximumColumn + " liegen."
                );
            }
            setCaretPosition(lineStart + zeroBasedColumn);
        } catch (BadLocationException exception) {
            throw new IllegalStateException("Die Spaltenposition konnte nicht ermittelt werden.", exception);
        }
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

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);

        Graphics2D guideGraphics = (Graphics2D) graphics.create();
        try {
            FontMetrics metrics = getFontMetrics(getFont());
            Insets insets = getInsets();
            double textStartX = insets.left;
            try {
                Rectangle2D firstCharacterBounds = modelToView2D(0);
                if (firstCharacterBounds != null) {
                    textStartX = firstCharacterBounds.getX();
                }
            } catch (BadLocationException ignored) {
                // Position 0 kann nur während eines Dokumentwechsels kurzzeitig fehlen.
            }
            int guideX = (int) Math.round(
                    textStartX + metrics.stringWidth(GUIDE_WIDTH_TEXT)
            );
            Rectangle visibleArea = getVisibleRect();
            Color foreground = getForeground();
            guideGraphics.setColor(new Color(
                    foreground.getRed(),
                    foreground.getGreen(),
                    foreground.getBlue(),
                    90
            ));
            guideGraphics.drawLine(
                    guideX,
                    visibleArea.y,
                    guideX,
                    visibleArea.y + visibleArea.height
            );
        } finally {
            guideGraphics.dispose();
        }
    }

    public record LoadResult(int characterCount, long byteCount) {
    }
}
