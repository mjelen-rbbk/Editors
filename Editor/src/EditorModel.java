
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.Reader;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.AccessDeniedException;
import java.nio.file.StandardCopyOption;
import java.text.BreakIterator;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.JTextArea;
import javax.swing.plaf.basic.BasicTextAreaUI;
import javax.swing.plaf.synth.SynthTextAreaUI;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.PlainDocument;
import javax.swing.text.Element;
import javax.swing.text.PlainView;
import javax.swing.text.View;

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
    // Spalten sind 1-basiert: Spalte 60 beginnt nach 59 Zeichen.
    private static final String GUIDE_WIDTH_TEXT = "0".repeat(GUIDE_COLUMN - 1);

    private int findPosition = -1;
    private Path filePath;
    private boolean dirty;
    private Integer preparedWordCount;

    @Override
    public void updateUI() {
        super.updateUI();
        if (getUI() instanceof SynthTextAreaUI) {
            setUI(new SynthTextAreaUI() {
                @Override
                public View create(Element element) {
                    return deferWidthCalculation(super.create(element), element);
                }
            });
        } else if (getUI().getClass() == BasicTextAreaUI.class) {
            setUI(new BasicTextAreaUI() {
                @Override
                public View create(Element element) {
                    return deferWidthCalculation(super.create(element), element);
                }
            });
        }
    }

    private static View deferWidthCalculation(View defaultView, Element element) {
        return defaultView instanceof PlainView
                ? new DeferredWidthPlainView(element) : defaultView;
    }

    public EditorModel() {
        DocumentListener dirtyListener = new DocumentListener() {
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
        };
        getDocument().addDocumentListener(dirtyListener);
        addPropertyChangeListener("document", event -> {
            ((Document) event.getOldValue()).removeDocumentListener(dirtyListener);
            Document newDocument = (Document) event.getNewValue();
            newDocument.removeDocumentListener(dirtyListener);
            newDocument.addDocumentListener(dirtyListener);
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
        return preparedWordCount != null ? preparedWordCount : countWords(getText());
    }

    public static int countWords(String text) {
        return countWords(text, false);
    }

    public static int countWordsInterruptibly(String text) {
        checkLoadCancelled();
        return countWords(text, true);
    }

    private static int countWords(String text, boolean cancellable) {
        BreakIterator words = BreakIterator.getWordInstance(Locale.GERMAN);
        words.setText(text);

        int wordCount = 0;
        int start = words.first();
        for (int end = words.next(); end != BreakIterator.DONE; start = end, end = words.next()) {
            if (cancellable) {
                checkLoadCancelled();
            }
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

    public void moveCaretToColumn(int oneBasedColumn) {
        if (oneBasedColumn < 1) {
            throw new IllegalArgumentException("Die Spalte muss mindestens 1 sein.");
        }

        try {
            int line = getLineOfOffset(getCaretPosition());
            int lineStart = getLineStartOffset(line);
            int lineEnd = getLineEndOffset(line);
            if (lineEnd > lineStart && "\n".equals(getText(lineEnd - 1, 1))) {
                lineEnd--;
                if (lineEnd > lineStart && "\r".equals(getText(lineEnd - 1, 1))) {
                    lineEnd--;
                }
            }

            int maximumColumn = lineEnd - lineStart + 1;
            if (oneBasedColumn > maximumColumn) {
                throw new IllegalArgumentException(
                        "Die Spalte muss in dieser Zeile zwischen 1 und "
                                + maximumColumn + " liegen."
                );
            }
            setCaretPosition(lineStart + oneBasedColumn - 1);
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
        return applyLoadedFile(readFile(path));
    }

    /** Bereitet ein noch nicht angezeigtes Dokument im Hintergrund vor. */
    public static LoadedFile readFile(Path path) throws IOException {
        Path normalizedPath = normalize(path);
        try {
            checkLoadCancelled();
            long byteCount = Files.size(normalizedPath);
            PlainDocument document = new PlainDocument();
            // Kein Swing-View und keine GUI-Listener hängen an diesem Dokument.
            // Auch der speicherintensive Zeilenindex entsteht vor der Übernahme.
            // FileChannel-I/O reagiert auf Thread-Unterbrechung; der Reader
            // behält UTF-8-Zeichen über Blockgrenzen hinweg korrekt bei.
            try (FileChannel channel = FileChannel.open(normalizedPath);
                    Reader reader = Channels.newReader(channel, StandardCharsets.UTF_8.newDecoder(), 8192)) {
                char[] buffer = new char[32768];
                while (true) {
                    checkLoadCancelled();
                    int count = reader.read(buffer);
                    if (count < 0) {
                        break;
                    }
                    checkLoadCancelled();
                    document.insertString(document.getLength(), new String(buffer, 0, count), null);
                }
            }
            checkLoadCancelled();
            int wordCount = countWords(document.getText(0, document.getLength()), true);
            checkLoadCancelled();
            return new LoadedFile(normalizedPath, document, byteCount, wordCount);
        } catch (BadLocationException exception) {
            throw new IOException("Das Textdokument konnte nicht vorbereitet werden.", exception);
        } catch (OutOfMemoryError error) {
            throw loadMemoryError(error);
        }
    }

    private static void checkLoadCancelled() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Laden abgebrochen.");
        }
    }

    /** Übernimmt vollständig gelesene Daten auf dem Swing-Event-Thread. */
    public LoadResult applyLoadedFile(LoadedFile loadedFile) throws IOException {
        Objects.requireNonNull(loadedFile);
        Document previousDocument = getDocument();
        int previousDot = getCaret().getDot();
        int previousMark = getCaret().getMark();
        int previousWordCount = getWordCount();
        try {
            preparedWordCount = loadedFile.wordCount();
            setDocument(loadedFile.document());
            setCaretPosition(getDocument().getLength());
        } catch (OutOfMemoryError error) {
            // Auch das Erzeugen des Swing-Views kann scheitern. Das alte
            // Dokument wurde nicht verändert und kann wieder eingesetzt werden.
            preparedWordCount = previousWordCount;
            setDocument(previousDocument);
            getCaret().setDot(previousMark);
            getCaret().moveDot(previousDot);
            throw loadMemoryError(error);
        } finally {
            preparedWordCount = null;
        }
        filePath = loadedFile.path();
        findPosition = -1;
        setDirty(false);

        return new LoadResult(loadedFile.document().getLength(), loadedFile.byteCount());
    }

    private static IOException loadMemoryError(OutOfMemoryError error) {
        return new IOException("Die Datei konnte nicht geladen oder angezeigt werden. "
                + "Der Java-Speicher oder die interne Textdarstellung reicht dafür nicht aus.", error);
    }

    public void save() throws IOException {
        if (filePath == null) {
            throw new IllegalStateException("Für das Dokument ist noch kein Dateipfad festgelegt.");
        }

        saveAs(filePath);
    }

    public void saveAs(Path path) throws IOException {
        Path normalizedPath = normalize(path);
        Path writePath = Files.isSymbolicLink(normalizedPath)
                ? normalizedPath.toRealPath() : normalizedPath;
        if (Files.exists(writePath) && !Files.isWritable(writePath)) {
            throw new AccessDeniedException(writePath.toString());
        }
        // Erst vollständig schreiben, dann ersetzen: Schreibfehler dürfen die
        // bisherige Datei nicht durch vorzeitiges Abschneiden beschädigen.
        Path temporaryFile = Files.createTempFile(writePath.getParent(), ".editor-", ".tmp");
        try {
            Files.writeString(temporaryFile, getText(), StandardCharsets.UTF_8);
            try {
                Files.move(temporaryFile, writePath,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryFile, writePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
        filePath = normalizedPath;
        setDirty(false);
    }

    private static Path normalize(Path path) {
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
        setCaretPosition(replacePosition + replaceString.length());
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

    public record LoadedFile(Path path, PlainDocument document, long byteCount, int wordCount) {
    }
}
