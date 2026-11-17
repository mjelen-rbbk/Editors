package xswing;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/**
 * Ein JPanel, das XY-Streudiagramme anzeigt und optional lineare
 * Regressionsgeraden sowie statistische Kennzahlen (Geradengleichung,
 * Korrelationskoeffizient) für bis zu vier Datensätze berechnen und darstellen
 * kann.
 * <p>
 * Diese Komponente ist für die Verwendung in GUI-Editoren (z.B. NetBeans)
 * optimiert, da sie über einen Standardkonstruktor mit Beispieldaten verfügt.
 * Daten und Darstellungsoptionen können über Setter-Methoden dynamisch geändert
 * werden.
 */
public class XyLinStatPanel extends JPanel {

    private static final int PREF_WIDTH = 800;
    private static final int PREF_HEIGHT = 400;
    private final int PADDING = 25;
    private final int LABEL_PADDING = 50;
    private final Color[] LINE_COLORS = {
        new Color(44, 102, 230, 180),
        new Color(44, 230, 102, 180),
        new Color(230, 88, 88, 180),
        new Color(200, 44, 200, 180)
    };
    private final Color GRID_COLOR = new Color(200, 200, 200, 200);
    private static final Stroke GRAPH_STROKE = new BasicStroke(2f);
    private static final Stroke REGRESSION_STROKE = new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{9}, 0);

    private final int Y_DIVISIONS = 10;
    private final int X_DIVISIONS = 10;
    private final int DEFAULT_POINT_WIDTH = 4;
    private final int LARGER_POINT_WIDTH = 8;

    private List<Dataset> datasets = new ArrayList<>();
    private Double manualMinX, manualMaxX, manualMinY, manualMaxY;

    /**
     * Erstellt ein neues XyLinStatPanel.
     * <p>
     * Initialisiert das Panel mit Beispieldaten, um eine visuelle Vorschau in
     * GUI-Editoren zu ermöglichen.
     */
    public XyLinStatPanel() {
        setPreferredSize(new Dimension(PREF_WIDTH, PREF_HEIGHT));
        double[] defaultX = {1, 2, 3, 4, 5, 6, 7};
        double[] defaultY = {1.8, 4.2, 5.8, 8.1, 10.3, 11.8, 14.2};
        addDataset(defaultX, defaultY, false, true); // Stats standardmäßig an
    }

    /**
     * Erstellt ein neues XyLinStatPanel und initialisiert es mit einem
     * Datensatz.
     *
     * @param xData die x-Werte des Datensatzes
     * @param yData die y-Werte des Datensatzes
     * @param lines {@code true}, um Punkte mit Linien zu verbinden, sonst
     * {@code false}
     * @param showStats {@code true}, um die Regressionsgerade und Statistiken
     * anzuzeigen
     */
    public XyLinStatPanel(double[] xData, double[] yData, boolean lines, boolean showStats) {
        this();
        datasets.clear();
        addDataset(xData, yData, lines, showStats);
    }

    /**
     * Fügt dem Plot einen neuen Datensatz hinzu. Es können maximal 4 Datensätze
     * hinzugefügt werden.
     *
     * @param xData die x-Werte des Datensatzes
     * @param yData die y-Werte des Datensatzes
     * @param lines {@code true}, um Punkte mit Linien zu verbinden
     * @param showStats {@code true}, um die Regressionsgerade und Statistiken
     * anzuzeigen
     */
    public void addDataset(double[] xData, double[] yData, boolean lines, boolean showStats) {
        if (datasets.size() >= 4) {
            return;
        }
        List<Double> xList = Arrays.stream(xData).boxed().collect(Collectors.toList());
        List<Double> yList = Arrays.stream(yData).boxed().collect(Collectors.toList());
        if (xList.size() != yList.size()) {
            throw new IllegalArgumentException("x and y arrays must have the same length");
        }
        Dataset dataset = new Dataset(xList, yList, lines, showStats, LINE_COLORS[datasets.size()]);
        datasets.add(dataset);
        if (showStats) {
            calculateStatsForDataset(dataset);
        }
    }

    /**
     * Entfernt alle Datensätze aus dem Plot.
     */
    public void clearDatasets() {
        datasets.clear();
        repaint();
    }

    private void updateDataset(int index, double[] xData, double[] yData, boolean lines, boolean showStats) {
        if (index < 0 || index >= 4) {
            throw new IllegalArgumentException("Index must be between 0 and 3.");
        }
        List<Double> xList = Arrays.stream(xData).boxed().collect(Collectors.toList());
        List<Double> yList = Arrays.stream(yData).boxed().collect(Collectors.toList());
        if (xList.size() != yList.size()) {
            throw new IllegalArgumentException("x and y arrays must have the same length");
        }

        Color color = (index < datasets.size() && datasets.get(index) != null) ? datasets.get(index).color : LINE_COLORS[index];
        Dataset newDataset = new Dataset(xList, yList, lines, showStats, color);

        while (datasets.size() <= index) {
            datasets.add(null);
        }
        datasets.set(index, newDataset);
        if (showStats) {
            calculateStatsForDataset(newDataset);
        }
        repaint();
    }

    /**
     * Setzt oder ersetzt den ersten Datensatz.
     *
     * @param xData die x-Werte
     * @param yData die y-Werte
     * @param lines {@code true} für Linien, {@code false} für Punkte
     * @param showStats {@code true} für statistische Auswertung
     */
    public void setDataset1(double[] xData, double[] yData, boolean lines, boolean showStats) {
        updateDataset(0, xData, yData, lines, showStats);
    }

    /**
     * Setzt oder ersetzt den zweiten Datensatz.
     *
     * @param xData die x-Werte
     * @param yData die y-Werte
     * @param lines {@code true} für Linien, {@code false} für Punkte
     * @param showStats {@code true} für statistische Auswertung
     */
    public void setDataset2(double[] xData, double[] yData, boolean lines, boolean showStats) {
        updateDataset(1, xData, yData, lines, showStats);
    }

    /**
     * Setzt oder ersetzt den dritten Datensatz.
     *
     * @param xData die x-Werte
     * @param yData die y-Werte
     * @param lines {@code true} für Linien, {@code false} für Punkte
     * @param showStats {@code true} für statistische Auswertung
     */
    public void setDataset3(double[] xData, double[] yData, boolean lines, boolean showStats) {
        updateDataset(2, xData, yData, lines, showStats);
    }

    /**
     * Setzt oder ersetzt den vierten Datensatz.
     *
     * @param xData die x-Werte
     * @param yData die y-Werte
     * @param lines {@code true} für Linien, {@code false} für Punkte
     * @param showStats {@code true} für statistische Auswertung
     */
    public void setDataset4(double[] xData, double[] yData, boolean lines, boolean showStats) {
        updateDataset(3, xData, yData, lines, showStats);
    }

    /**
     * Zeichnet die Komponente, einschließlich Gitter, Achsen, Daten und
     * statistischer Auswertungen. Diese Methode wird vom Swing-Framework
     * aufgerufen und sollte nicht direkt verwendet werden.
     *
     * @param g das vom System bereitgestellte {@code Graphics}-Objekt
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        double minX = calculateMinX();
        double maxX = calculateMaxX();
        double minY = calculateMinY();
        double maxY = calculateMaxY();

        if (minX == maxX) {
            maxX = minX + 1;
        }
        if (minY == maxY) {
            maxY = minY + 1;
        }

        double xScale = (getWidth() - 2 * PADDING - LABEL_PADDING) / (maxX - minX);
        double yScale = (getHeight() - 2 * PADDING - LABEL_PADDING) / (maxY - minY);

        g2.setColor(Color.WHITE);
        g2.fillRect(PADDING + LABEL_PADDING, PADDING, getWidth() - 2 * PADDING - LABEL_PADDING, getHeight() - 2 * PADDING - LABEL_PADDING);

        drawGrid(g2, minX, maxX, minY, maxY);

        for (Dataset dataset : datasets) {
            if (dataset != null) {
                drawDataset(g2, dataset, minX, minY, xScale, yScale);
                if (dataset.showStats && dataset.statsCalculated) {
                    drawRegressionLine(g2, dataset, minX, maxX, minY, xScale, yScale);
                }
            }
        }

        drawStatsText(g2);
    }

    private void calculateStatsForDataset(Dataset dataset) {
        if (dataset.x.size() < 2) {
            dataset.statsCalculated = false;
            return;
        }

        int n = dataset.x.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0, sumY2 = 0;

        for (int i = 0; i < n; i++) {
            double x = dataset.x.get(i);
            double y = dataset.y.get(i);
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
            sumY2 += y * y;
        }

        double denominator = (n * sumX2 - sumX * sumX);
        if (Math.abs(denominator) < 1e-9) {
            dataset.statsCalculated = false;
            return;
        }

        dataset.slope = (n * sumXY - sumX * sumY) / denominator;
        dataset.intercept = (sumY - dataset.slope * sumX) / n;

        double corrDenominator = Math.sqrt((n * sumX2 - sumX * sumX) * (n * sumY2 - sumY * sumY));
        if (Math.abs(corrDenominator) < 1e-9) {
            dataset.correlation = 0;
        } else {
            dataset.correlation = (n * sumXY - sumX * sumY) / corrDenominator;
        }

        dataset.statsCalculated = true;
    }

    private void drawRegressionLine(Graphics2D g2, Dataset dataset, double minX, double maxX, double minY, double xScale, double yScale) {
        double startY = dataset.slope * minX + dataset.intercept;
        double endY = dataset.slope * maxX + dataset.intercept;

        int x1 = (int) (PADDING + LABEL_PADDING);
        int y1 = (int) (getHeight() - PADDING - LABEL_PADDING - (startY - minY) * yScale);
        int x2 = (int) (getWidth() - PADDING);
        int y2 = (int) (getHeight() - PADDING - LABEL_PADDING - (endY - minY) * yScale);

        Stroke oldStroke = g2.getStroke();
        g2.setColor(dataset.color);
        g2.setStroke(REGRESSION_STROKE);
        g2.drawLine(x1, y1, x2, y2);
        g2.setStroke(oldStroke);
    }

    private void drawStatsText(Graphics2D g2) {
        int yPos = PADDING;
        FontMetrics metrics = g2.getFontMetrics();

        for (int i = 0; i < datasets.size(); i++) {
            Dataset dataset = datasets.get(i);
            if (dataset != null && dataset.showStats && dataset.statsCalculated) {
                g2.setColor(dataset.color);
                String equation = String.format("y = %.2fx + %.2f", dataset.slope, dataset.intercept);
                String correlation = String.format("r = %.3f", dataset.correlation);

                g2.drawString(equation, PADDING + LABEL_PADDING + 5, yPos + metrics.getAscent());
                yPos += metrics.getHeight();
                g2.drawString(correlation, PADDING + LABEL_PADDING + 5, yPos + metrics.getAscent());
                yPos += metrics.getHeight() * 1.5;
            }
        }
    }

    private void drawGrid(Graphics2D g2, double minX, double maxX, double minY, double maxY) {
        g2.setColor(Color.BLACK);
        int yDecimals = calculateDecimalPlaces(minY, maxY, Y_DIVISIONS);
        for (int i = 0; i <= Y_DIVISIONS; i++) {
            int yPos = getHeight() - PADDING - LABEL_PADDING - (i * (getHeight() - 2 * PADDING - LABEL_PADDING)) / Y_DIVISIONS;
            double value = minY + (maxY - minY) * (i / (double) Y_DIVISIONS);
            String label = formatLabel(value, yDecimals);
            FontMetrics metrics = g2.getFontMetrics();
            int labelWidth = metrics.stringWidth(label);
            g2.drawString(label, (PADDING + LABEL_PADDING) - labelWidth - 5, yPos + metrics.getHeight() / 2);
            g2.setColor(GRID_COLOR);
            g2.drawLine(PADDING + LABEL_PADDING, yPos, getWidth() - PADDING, yPos);
            g2.setColor(Color.BLACK);
        }

        int xDecimals = calculateDecimalPlaces(minX, maxX, X_DIVISIONS);
        int xAxisY = getHeight() - PADDING - LABEL_PADDING; // Y-Position der X-Achse
        for (int i = 0; i <= X_DIVISIONS; i++) {
            int x = PADDING + LABEL_PADDING + (i * (getWidth() - 2 * PADDING - LABEL_PADDING)) / X_DIVISIONS;
            double value = minX + (maxX - minX) * (i / (double) X_DIVISIONS);
            String label = formatLabel(value, xDecimals);
            FontMetrics metrics = g2.getFontMetrics();
            int labelWidth = metrics.stringWidth(label);
            // KORRIGIERTE POSITIONIERUNG
            g2.drawString(label, x - labelWidth / 2, xAxisY + metrics.getHeight() + 3);
            g2.setColor(GRID_COLOR);
            g2.drawLine(x, PADDING, x, xAxisY);
            g2.setColor(Color.BLACK);
        }

        g2.drawLine(PADDING + LABEL_PADDING, xAxisY, PADDING + LABEL_PADDING, PADDING);
        g2.drawLine(PADDING + LABEL_PADDING, xAxisY, getWidth() - PADDING, xAxisY);
    }

    private void drawDataset(Graphics2D g2, Dataset dataset, double minX, double minY, double xScale, double yScale) {
        List<Point> points = new ArrayList<>();
        for (int i = 0; i < dataset.x.size(); i++) {
            double xVal = dataset.x.get(i);
            double yVal = dataset.y.get(i);
            if (isInBounds(xVal, yVal)) {
                int x = (int) (PADDING + LABEL_PADDING + (xVal - minX) * xScale);
                int y = (int) (getHeight() - PADDING - LABEL_PADDING - (yVal - minY) * yScale);
                points.add(new Point(x, y));
            }
        }
        if (dataset.lines) {
            g2.setColor(dataset.color);
            g2.setStroke(GRAPH_STROKE);
            for (int i = 0; i < points.size() - 1; i++) {
                g2.drawLine(points.get(i).x, points.get(i).y, points.get(i + 1).x, points.get(i + 1).y);
            }
        }
        g2.setColor(dataset.color.darker());
        int pointSize = dataset.lines ? DEFAULT_POINT_WIDTH : LARGER_POINT_WIDTH;
        for (Point p : points) {
            g2.fillOval(p.x - pointSize / 2, p.y - pointSize / 2, pointSize, pointSize);
        }
    }

    private int calculateDecimalPlaces(double min, double max, int divisions) {
        if (max - min == 0) {
            return 0;
        }
        double step = (max - min) / divisions;
        if (step >= 1) {
            return 0;
        }
        if (step >= 0.1) {
            return 1;
        }
        if (step >= 0.01) {
            return 2;
        }
        return 3;
    }

    private String formatLabel(double value, int decimals) {
        if (decimals == 0) {
            return String.format("%d", (int) Math.round(value));
        }
        return String.format("%." + decimals + "f", value).replaceAll("\\.0+$", "");
    }

    private boolean isInBounds(double x, double y) {
        return (manualMinX == null || x >= manualMinX) && (manualMaxX == null || x <= manualMaxX)
                && (manualMinY == null || y >= manualMinY) && (manualMaxY == null || y <= manualMaxY);
    }

    private double calculateMinX() {
        return manualMinX != null ? manualMinX : datasets.stream().filter(d -> d != null)
                .flatMap(d -> d.x.stream()).min(Double::compare).orElse(0.0);
    }

    private double calculateMaxX() {
        return manualMaxX != null ? manualMaxX : datasets.stream().filter(d -> d != null)
                .flatMap(d -> d.x.stream()).max(Double::compare).orElse(1.0);
    }

    private double calculateMinY() {
        return manualMinY != null ? manualMinY : datasets.stream().filter(d -> d != null)
                .flatMap(d -> d.y.stream()).min(Double::compare).orElse(0.0);
    }

    private double calculateMaxY() {
        return manualMaxY != null ? manualMaxY : datasets.stream().filter(d -> d != null)
                .flatMap(d -> d.y.stream()).max(Double::compare).orElse(1.0);
    }

    /**
     * Setzt einen festen, manuellen Bereich für die X-Achse. Dies überschreibt
     * die automatische Skalierung.
     *
     * @param min der anzuzeigende Minimalwert der X-Achse
     * @param max der anzuzeigende Maximalwert der X-Achse
     */
    public void setManualXRange(double min, double max) {
        manualMinX = min;
        manualMaxX = max;
        repaint();
    }

    /**
     * Setzt einen festen, manuellen Bereich für die Y-Achse. Dies überschreibt
     * die automatische Skalierung.
     *
     * @param min der anzuzeigende Minimalwert der Y-Achse
     * @param max der anzuzeigende Maximalwert der Y-Achse
     */
    public void setManualYRange(double min, double max) {
        manualMinY = min;
        manualMaxY = max;
        repaint();
    }

    /**
     * Erstellt und zeigt den Plot mit einem Datensatz in einem modalen Dialog
     * an.
     *
     * @param x die x-Werte
     * @param y die y-Werte
     * @param lines {@code true} für Linien, {@code false} für Punkte
     * @param showStats {@code true} für statistische Auswertung
     * @param title der Titel des Dialogfensters
     */
    public static void show(double[] x, double[] y, boolean lines, boolean showStats, String title) {
        XyLinStatPanel panel = new XyLinStatPanel();
        panel.clearDatasets();
        panel.addDataset(x, y, lines, showStats);
        showDialog(panel, title);
    }

    /**
     * Erstellt und zeigt den Plot mit zwei Datensätzen in einem modalen Dialog
     * an.
     *
     * @param x1 x-Werte für Datensatz 1
     * @param y1 y-Werte für Datensatz 1
     * @param lines1 Linien für Datensatz 1
     * @param showStats1 Statistiken für Datensatz 1
     * @param x2 x-Werte für Datensatz 2
     * @param y2 y-Werte für Datensatz 2
     * @param lines2 Linien für Datensatz 2
     * @param showStats2 Statistiken für Datensatz 2
     * @param title der Titel des Dialogfensters
     */
    public static void show(double[] x1, double[] y1, boolean lines1, boolean showStats1,
            double[] x2, double[] y2, boolean lines2, boolean showStats2,
            String title) {
        XyLinStatPanel panel = new XyLinStatPanel();
        panel.clearDatasets();
        panel.addDataset(x1, y1, lines1, showStats1);
        panel.addDataset(x2, y2, lines2, showStats2);
        showDialog(panel, title);
    }

    /**
     * Erstellt und zeigt den Plot mit drei Datensätzen in einem modalen Dialog
     * an.
     *
     * @param x1 x-Werte für Datensatz 1
     * @param y1 y-Werte für Datensatz 1
     * @param lines1 Linien für Datensatz 1
     * @param showStats1 Statistiken für Datensatz 1
     * @param x2 x-Werte für Datensatz 2
     * @param y2 y-Werte für Datensatz 2
     * @param lines2 Linien für Datensatz 2
     * @param showStats2 Statistiken für Datensatz 2
     * @param x3 x-Werte für Datensatz 3
     * @param y3 y-Werte für Datensatz 3
     * @param lines3 Linien für Datensatz 3
     * @param showStats3 Statistiken für Datensatz 3
     * @param title der Titel des Dialogfensters
     */
    public static void show(double[] x1, double[] y1, boolean lines1, boolean showStats1,
            double[] x2, double[] y2, boolean lines2, boolean showStats2,
            double[] x3, double[] y3, boolean lines3, boolean showStats3,
            String title) {
        XyLinStatPanel panel = new XyLinStatPanel();
        panel.clearDatasets();
        panel.addDataset(x1, y1, lines1, showStats1);
        panel.addDataset(x2, y2, lines2, showStats2);
        panel.addDataset(x3, y3, lines3, showStats3);
        showDialog(panel, title);
    }

    /**
     * Erstellt und zeigt den Plot mit vier Datensätzen in einem modalen Dialog
     * an.
     *
     * @param x1 x-Werte für Datensatz 1
     * @param y1 y-Werte für Datensatz 1
     * @param lines1 Linien für Datensatz 1
     * @param showStats1 Statistiken für Datensatz 1
     * @param x2 x-Werte für Datensatz 2
     * @param y2 y-Werte für Datensatz 2
     * @param lines2 Linien für Datensatz 2
     * @param showStats2 Statistiken für Datensatz 2
     * @param x3 x-Werte für Datensatz 3
     * @param y3 y-Werte für Datensatz 3
     * @param lines3 Linien für Datensatz 3
     * @param showStats3 Statistiken für Datensatz 3
     * @param x4 x-Werte für Datensatz 4
     * @param y4 y-Werte für Datensatz 4
     * @param lines4 Linien für Datensatz 4
     * @param showStats4 Statistiken für Datensatz 4
     * @param title der Titel des Dialogfensters
     */
    public static void show(double[] x1, double[] y1, boolean lines1, boolean showStats1,
            double[] x2, double[] y2, boolean lines2, boolean showStats2,
            double[] x3, double[] y3, boolean lines3, boolean showStats3,
            double[] x4, double[] y4, boolean lines4, boolean showStats4,
            String title) {
        XyLinStatPanel panel = new XyLinStatPanel();
        panel.clearDatasets();
        panel.addDataset(x1, y1, lines1, showStats1);
        panel.addDataset(x2, y2, lines2, showStats2);
        panel.addDataset(x3, y3, lines3, showStats3);
        panel.addDataset(x4, y4, lines4, showStats4);
        showDialog(panel, title);
    }

    private static void showDialog(JPanel panel, String title) {
        JDialog dialog = new JDialog();
        dialog.setTitle(title);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setModal(true);
        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> dialog.dispose());
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 2));
        buttonPanel.add(okButton);
        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.add(panel, BorderLayout.CENTER);
        contentPanel.add(buttonPanel, BorderLayout.SOUTH);
        okButton.getInputMap(JButton.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ENTER"), "press");
        okButton.getActionMap().put("press", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dialog.dispose();
            }
        });
        dialog.setContentPane(contentPanel);
        dialog.pack();
        dialog.setLocationRelativeTo(null);
        SwingUtilities.invokeLater(okButton::requestFocusInWindow);
        dialog.setVisible(true);
    }

    private static class Dataset {

        List<Double> x;
        List<Double> y;
        boolean lines;
        boolean showStats;
        Color color;
        double slope, intercept, correlation;
        boolean statsCalculated = false;

        Dataset(List<Double> x, List<Double> y, boolean lines, boolean showStats, Color color) {
            this.x = x;
            this.y = y;
            this.lines = lines;
            this.showStats = showStats;
            this.color = color;
        }
    }

    // Beispielaufruf
    public static void main(String[] args) {
        // Beispiel 1: Lineare Daten
        double[] x1 = {1, 2, 3, 4, 5};
        double[] y1 = {2, 4, 3, 5, 6};
        double[] y2 = {1, 3, 2, 4, 5};

        XyLinStatPanel.show(
                x1, y1, true, true, // Mit Gerade und Statistik
                x1, y2, false, false, // Nur Punkte
                "Beispiel 1: Lineare Daten"
        );

        XyLinStatPanel.show(
                x1, y2, true, true, // Ausgleichsgerade
                x1, x1, false, true, // f(x) = x
                "Beispiel 2: Sehr lineare Daten"
        );

        // Beispiel 3: Quadratische Funktion
        double[] x3 = {-5, -4, -3, -2, -1, 0, 1, 2, 3, 4, 5};
        double[] y3 = Arrays.stream(x3).map(x -> x * x).toArray();

        XyLinStatPanel.show(
                x3, y3, false, true, // ohne Gerade mit Statistik
                "Beispiel 3: Quadratische Funktion"
        );

        // Beispiel 4: Exponentielles Wachstum
        double[] x4 = new double[10];
        double[] yExp = new double[10];
        for (int i = 0; i < 10; i++) {
            x4[i] = i;
            yExp[i] = Math.exp(i / 3.0);
        }

        XyLinStatPanel.show(
                x4, yExp, true, true, // mit Linienzug,  mit Statistik
                "Beispiel 4: Exponentielles Wachstum"
        );

        // Beispiel 5: Zufällige Punkte
        double[] x5 = new double[20];
        double[] y5 = new double[20];
        for (int i = 0; i < 20; i++) {
            x5[i] = Math.random() * 10;
            y5[i] = Math.random() * 10;
        }

        XyLinStatPanel.show(
                x5, y5, false, true,
                "Beispiel 5: Zufällige Punkte"
        );
    }
}

class XyStatistics {

    private final double[] x;
    private final double[] y;
    private int n;
    private double sx;
    private double sy;
    private double sxx;
    private double syy;
    private double sxy;
    private double x_mean;
    private double y_mean;
    private double m_est;
    private double b_est;
    private double vxx;
    private double vyy;
    private double vxy;
    private double veyey;

    public XyStatistics(double[] y) {
        this.y = new double[y.length];
        this.x = new double[y.length];
        for (int i = 0; i < y.length; ++i) {
            this.x[i] = i;
            this.y[i] = y[i];
        }
        init();
    }

    public XyStatistics(double[] x, double[] y) {
        this.y = new double[y.length];
        this.x = new double[y.length];
        for (int i = 0; i < y.length; ++i) {
            this.x[i] = x[i];
            this.y[i] = y[i];
        }
        init();
    }

    public XyStatistics(int[] x, double[] y) {
        this.y = new double[y.length];
        this.x = new double[y.length];
        for (int i = 0; i < y.length; ++i) {
            this.x[i] = x[i];
            this.y[i] = y[i];
        }
        init();
    }

    public XyStatistics(double[] x, int[] y) {
        this.y = new double[y.length];
        this.x = new double[y.length];
        for (int i = 0; i < y.length; ++i) {
            this.x[i] = x[i];
            this.y[i] = y[i];
        }
        init();
    }

    public XyStatistics(int[] x, int[] y) {
        this.y = new double[y.length];
        this.x = new double[y.length];
        for (int i = 0; i < y.length; ++i) {
            this.x[i] = x[i];
            this.y[i] = y[i];
        }
        init();
    }

    private void init() {
        n = x.length;
        if (n < 2) { // Statistische Berechnung erst ab 2 Punkten sinnvoll
            return;
        }

        for (int i = 0; i < n; ++i) {
            double xi = x[i];
            double yi = y[i];
            sx += xi;
            sy += yi;
            sxx += xi * xi;
            syy += yi * yi;
            sxy += xi * yi;
        }

        x_mean = sx / n;
        y_mean = sy / n;

        // Summe der Quadrate (Sum of Squares) - numerisch stabiler
        double ssxx = sxx - sx * sx / n;
        double ssyy = syy - sy * sy / n;
        double ssxy = sxy - sx * sy / n;

        // Schätzer für Steigung (m) und y-Achsenabschnitt (b)
        if (ssxx != 0) {
            m_est = ssxy / ssxx;
            b_est = y_mean - m_est * x_mean;
        } else {
            m_est = Double.NaN; // Vertikale Linie, Steigung undefiniert
            b_est = y_mean;
        }

        // Varianzen und Kovarianz
        vxx = ssxx / (n - 1);
        vyy = ssyy / (n - 1);
        vxy = ssxy / (n - 1);

        // Varianz des Schätzfehlers
        veyey = 0;
        if (n > 2 && !Double.isNaN(m_est)) {
            double sumOfSquaredErrors = 0;
            for (int i = 0; i < n; ++i) {
                double py = getY_est(x[i]);
                double ey = y[i] - py;
                sumOfSquaredErrors += ey * ey;
            }
            veyey = sumOfSquaredErrors / (n - 2);
        }
    }

    /**
     * Sum of x(i).
     *
     * @return Sx
     */
    public double getSx() {
        return sx;
    }

    /**
     * Sum of y(i).
     *
     * @return Sy
     */
    public double getSy() {
        return sy;
    }

    /**
     * Sum of x²(i).
     *
     * @return Sxx
     */
    public double getSxx() {
        return sxx;
    }

    /**
     * Sum of y²(i).
     *
     * @return Syy
     */
    public double getSyy() {
        return syy;
    }

    /**
     * Sum of x(i) * y(i).
     *
     * @return Sxy
     */
    public double getSxy() {
        return sxy;
    }

    /**
     * Mean value of x = Sx/n.
     *
     * @return x_avg
     */
    public double getX_mean() {
        return x_mean;
    }

    /**
     * Mean value of y = Sy/n.
     *
     * @return y_avg
     */
    public double getY_mean() {
        return y_mean;
    }

    /**
     * Gradient m of the linear regression y = m x + b.
     *
     * @return Estimate of m
     */
    public double getM_est() {
        return m_est;
    }

    /**
     * Bias b of the linear regression y = m x + b.
     *
     * @return Estimate of b
     */
    public double getB_est() {
        return b_est;
    }

    /**
     * Linear regression estimate for ŷ = m x + b.
     *
     * @param x
     * @return ŷ
     */
    public double getY_est(double x) {
        return m_est * x + b_est;
    }

    /**
     * Linear regression estimate for ŷ = m x + b.
     *
     * @param x Array (double)
     * @return ŷ Array
     */
    public double[] getY_est(double[] x) {
        double[] y_est = new double[x.length];
        for (int i = 0; i < x.length; ++i) {
            y_est[i] = getY_est(x[i]);
        }
        return y_est;
    }

    /**
     * Linear regression estimate for ŷ = m x + b.
     *
     * @param xi Array (int)
     * @return ŷ Array
     */
    public double[] getY_est(int[] xi) {
        double[] y_est = new double[xi.length];
        for (int i = 0; i < xi.length; ++i) {
            y_est[i] = getY_est(xi[i]);
        }
        return y_est;
    }

    /**
     * Variance of x.
     *
     * @return Vxx
     */
    public double getVxx() {
        return vxx;
    }

    /**
     * Variance of y.
     *
     * @return Vyy
     */
    public double getVyy() {
        return vyy;
    }

    /**
     * Standard deviation of x.
     *
     * @return Sigma_x
     */
    public double getSigmaX() {
        return Math.sqrt(vxx);
    }

    /**
     * Standard deviation of y.
     *
     * @return Sigma_y
     */
    public double getSigmaY() {
        return Math.sqrt(vyy);
    }

    /**
     * Covariance of x and y.
     *
     * @return Vxy
     */
    public double getVxy() {
        return vxy;
    }

    /**
     * Correlation between x and y.
     *
     * @return rxy [-1 ... 1]
     */
    public double getRxy() {
        // Division durch 0 vermeiden, falls eine der Varianzen 0 ist
        if (vxx == 0 || vyy == 0) {
            return Double.NaN;
        }
        return vxy / Math.sqrt(vxx * vyy);
    }

    /**
     * Distribution interval of the prediction ŷ = m x + b. Two thirds of all
     * prediction values are in the interval [ mx + b - sigma_py, mx + b +
     * sigma_py]
     *
     * @return Sigma_py
     */
    public double getSigma_py() {
        return Math.sqrt(veyey);
    }

    /**
     * Variance of the prediction error e(i) = y(i) - py(i)
     *
     * @return Vpypy
     */
    public double getVeyey() {
        return veyey;
    }

}
