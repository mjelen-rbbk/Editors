import java.awt.Component;
import java.awt.Shape;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.text.Element;
import javax.swing.text.Document;
import javax.swing.text.PlainView;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;

/** Bündelt Swings Vollsuche nach der längsten Zeile beim Rückwärtslöschen. */
final class DeferredWidthPlainView extends PlainView {
    private final Timer widthTimer;
    private DocumentEvent pendingChange;
    private Shape pendingAllocation;
    private ViewFactory pendingFactory;
    private float widthBeforeDeletion;
    private float lastKnownWidth;
    private Element pendingLine;

    DeferredWidthPlainView(Element element) {
        super(element);
        widthTimer = new Timer(750, event -> flushWidthUpdate());
        widthTimer.setRepeats(false);
    }

    @Override
    protected void updateDamage(DocumentEvent change, Shape allocation, ViewFactory factory) {
        if (change.getType() == DocumentEvent.EventType.REMOVE
                && change.getChange(getElement()) == null) {
            Element lineElement = getElement().getElement(
                    getElement().getElementIndex(change.getOffset()));
            if (pendingChange != null && pendingLine != lineElement) {
                flushWidthUpdate();
            }
            // Keine Zeile entfernt: Text/Caret sofort zeichnen, aber die teure
            // Suche nach einer neuen längsten Zeile erst nach der Löschserie.
            if (pendingChange == null) {
                widthBeforeDeletion = Math.max(lastKnownWidth, super.getPreferredSpan(View.X_AXIS));
            }
            pendingChange = change;
            pendingLine = lineElement;
            pendingAllocation = allocation == null ? null : allocation.getBounds();
            pendingFactory = factory;
            widthTimer.restart();
            int line = getElement().getElementIndex(change.getOffset());
            Component host = getContainer();
            if (host != null) {
                damageLineRange(line, line, allocation, host);
            }
            return;
        }
        // Bei neuen/entfernten Zeilen bleibt die normale Swing-Verarbeitung.
        flushWidthUpdate();
        super.updateDamage(change, allocation, factory);
    }

    @Override
    public float getPreferredSpan(int axis) {
        float span = super.getPreferredSpan(axis);
        if (axis == View.X_AXIS && pendingChange == null) {
            lastKnownWidth = span;
        }
        return axis == View.X_AXIS && pendingChange != null
                ? Math.max(span, widthBeforeDeletion) : span;
    }

    private void flushWidthUpdate() {
        widthTimer.stop();
        DocumentEvent change = pendingChange;
        Shape allocation = pendingAllocation;
        ViewFactory factory = pendingFactory;
        Element line = pendingLine;
        pendingChange = null;
        pendingLine = null;
        pendingAllocation = null;
        pendingFactory = null;
        if (change != null && getParent() != null) {
            // Die aktuelle Position der Zeile verwenden, falls spätere Änderungen
            // vor ihr den ursprünglichen Ereignis-Offset verschoben haben.
            super.updateDamage(new WidthChange(change, line), allocation, factory);
        }
    }

    @Override
    public void setParent(View parent) {
        if (parent == null && widthTimer != null) {
            widthTimer.stop();
            pendingChange = null;
            pendingLine = null;
            pendingAllocation = null;
            pendingFactory = null;
        }
        super.setParent(parent);
    }

    private record WidthChange(DocumentEvent original, Element line) implements DocumentEvent {
        @Override public int getOffset() {
            return Math.min(line.getStartOffset(), getDocument().getLength());
        }
        @Override public int getLength() { return original.getLength(); }
        @Override public Document getDocument() { return original.getDocument(); }
        @Override public EventType getType() { return original.getType(); }
        @Override public ElementChange getChange(Element element) { return null; }
    }
}
