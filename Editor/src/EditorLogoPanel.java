import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import javax.swing.JPanel;

public class EditorLogoPanel extends JPanel {
    private static final Color OUTLINE_COLOR = new Color(35, 91, 125);
    private static final Color PAPER_COLOR = new Color(250, 252, 255);
    private static final Color FOLD_COLOR = new Color(174, 216, 235);
    private static final Color PENCIL_COLOR = new Color(242, 157, 43);
    private static final Color ERASER_COLOR = new Color(205, 83, 96);

    public EditorLogoPanel() {
        setOpaque(false);
        setPreferredSize(new Dimension(40, 32));
        setMinimumSize(new Dimension(32, 24));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);

        Graphics2D logoGraphics = (Graphics2D) graphics.create();
        try {
            logoGraphics.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            int padding = Math.max(2, Math.round(Math.min(getWidth(), getHeight()) * 0.08f));
            int logoSize = Math.max(0, Math.min(getWidth(), getHeight()) - 2 * padding);
            if (logoSize < 8) {
                return;
            }

            int x = (getWidth() - logoSize) / 2;
            int y = (getHeight() - logoSize) / 2;
            float documentX = x + logoSize * 0.10f;
            float documentY = y + logoSize * 0.05f;
            float documentWidth = logoSize * 0.68f;
            float documentHeight = logoSize * 0.88f;
            float foldSize = logoSize * 0.22f;

            Path2D.Float document = new Path2D.Float();
            document.moveTo(documentX, documentY);
            document.lineTo(documentX + documentWidth - foldSize, documentY);
            document.lineTo(documentX + documentWidth, documentY + foldSize);
            document.lineTo(documentX + documentWidth, documentY + documentHeight);
            document.lineTo(documentX, documentY + documentHeight);
            document.closePath();

            logoGraphics.setColor(PAPER_COLOR);
            logoGraphics.fill(document);
            logoGraphics.setStroke(new BasicStroke(
                    Math.max(1.2f, logoSize * 0.055f),
                    BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND
            ));
            logoGraphics.setColor(OUTLINE_COLOR);
            logoGraphics.draw(document);

            Path2D.Float fold = new Path2D.Float();
            fold.moveTo(documentX + documentWidth - foldSize, documentY);
            fold.lineTo(documentX + documentWidth - foldSize, documentY + foldSize);
            fold.lineTo(documentX + documentWidth, documentY + foldSize);
            fold.closePath();
            logoGraphics.setColor(FOLD_COLOR);
            logoGraphics.fill(fold);
            logoGraphics.setColor(OUTLINE_COLOR);
            logoGraphics.draw(fold);

            logoGraphics.setStroke(new BasicStroke(
                    Math.max(1.0f, logoSize * 0.045f),
                    BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND
            ));
            float textLeft = documentX + logoSize * 0.12f;
            float textRight = documentX + documentWidth - logoSize * 0.13f;
            float firstLineY = documentY + logoSize * 0.38f;
            float secondLineY = documentY + logoSize * 0.53f;
            logoGraphics.drawLine(
                    Math.round(textLeft), Math.round(firstLineY),
                    Math.round(textRight), Math.round(firstLineY)
            );
            logoGraphics.drawLine(
                    Math.round(textLeft), Math.round(secondLineY),
                    Math.round(textRight - logoSize * 0.10f), Math.round(secondLineY)
            );

            float pencilStartX = x + logoSize * 0.43f;
            float pencilStartY = y + logoSize * 0.82f;
            float pencilEndX = x + logoSize * 0.91f;
            float pencilEndY = y + logoSize * 0.34f;
            logoGraphics.setStroke(new BasicStroke(
                    Math.max(3.0f, logoSize * 0.17f),
                    BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND
            ));
            logoGraphics.setColor(OUTLINE_COLOR);
            logoGraphics.drawLine(
                    Math.round(pencilStartX), Math.round(pencilStartY),
                    Math.round(pencilEndX), Math.round(pencilEndY)
            );
            logoGraphics.setStroke(new BasicStroke(
                    Math.max(1.5f, logoSize * 0.10f),
                    BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND
            ));
            logoGraphics.setColor(PENCIL_COLOR);
            logoGraphics.drawLine(
                    Math.round(pencilStartX), Math.round(pencilStartY),
                    Math.round(pencilEndX), Math.round(pencilEndY)
            );
            logoGraphics.setColor(ERASER_COLOR);
            logoGraphics.drawLine(
                    Math.round(pencilStartX), Math.round(pencilStartY),
                    Math.round(pencilStartX + logoSize * 0.07f),
                    Math.round(pencilStartY - logoSize * 0.07f)
            );
        } finally {
            logoGraphics.dispose();
        }
    }
}
