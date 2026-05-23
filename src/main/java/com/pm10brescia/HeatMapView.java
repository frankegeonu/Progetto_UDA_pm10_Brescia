package com.pm10brescia;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Componente visuale custom che mostra una mappa di calore PM10.
 *
 * Layout della griglia:
 *   - Righe  = ore del giorno (0–23)
 *   - Colonne = ultimi 7 giorni
 *   - Colore  = media PM10 in quell'ora/giorno (verde→giallo→rosso)
 *
 * Non esiste un nodo JavaFX nativo per le heatmap: la griglia è costruita
 * manualmente con un GridPane di Rectangle colorati.
 */
public class HeatMapView {

    // Soglia UE giornaliera PM10 (µg/m³) — usata per scalare i colori
    private static final double SOGLIA_UE = 50.0;

    // Dimensioni di ogni cella
    private static final double CELL_W = 54;
    private static final double CELL_H = 18;

    private static final DateTimeFormatter FMT_GIORNO = DateTimeFormatter.ofPattern("dd/MM\nEEE",
            java.util.Locale.ITALIAN);
    private static final DateTimeFormatter FMT_TOOLTIP = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final BorderPane container;

    public HeatMapView() {
        container = new BorderPane();
        container.setStyle("-fx-background-color: #0f1117;");
        container.setPadding(new Insets(16));

        Label placeholder = new Label("Carica i dati per visualizzare la mappa di calore");
        placeholder.setStyle("-fx-text-fill: #5a6278; -fx-font-size: 13; -fx-font-family: Consolas;");
        container.setCenter(placeholder);
    }

    /** Restituisce il contenitore da inserire nel layout principale. */
    public BorderPane getContainer() {
        return container;
    }

    /**
     * Ridisegna la mappa di calore con i nuovi dati.
     *
     * @param dati mappa sensore → lista letture (già filtrate)
     */
    public void aggiorna(Map<String, List<PM10Reading>> dati) {
        if (dati.isEmpty()) return;

        // 1) Calcola la media tra tutti i sensori per ogni (giorno, ora)
        //    Struttura: giorno-indice → ora → lista valori
        TreeMap<String, Map<Integer, List<Double>>> griglia = new TreeMap<>();

        for (List<PM10Reading> letture : dati.values()) {
            for (PM10Reading r : letture) {
                String giornoKey = r.getTimestamp().toLocalDate().toString();
                int ora = r.getTimestamp().getHour();
                griglia.computeIfAbsent(giornoKey, k -> new HashMap<>())
                       .computeIfAbsent(ora, k -> new ArrayList<>())
                       .add(r.getValue());
            }
        }

        // Prende gli ultimi 7 giorni presenti nei dati
        List<String> giorni = new ArrayList<>(griglia.descendingKeySet());
        if (giorni.size() > 7) giorni = giorni.subList(0, 7);
        Collections.reverse(giorni); // ordine cronologico → sinistra = più vecchio

        // Calcola min e max globali per scalare i colori
        double maxVal = griglia.values().stream()
                .flatMap(m -> m.values().stream())
                .flatMap(List::stream)
                .mapToDouble(Double::doubleValue)
                .max().orElse(SOGLIA_UE);
        double minVal = griglia.values().stream()
                .flatMap(m -> m.values().stream())
                .flatMap(List::stream)
                .mapToDouble(Double::doubleValue)
                .min().orElse(0);

        // 2) Costruisce il GridPane
        GridPane grid = new GridPane();
        grid.setHgap(2);
        grid.setVgap(2);

        // Intestazione colonne (giorni)
        grid.add(emptyCell(), 0, 0); // cella vuota angolo in alto a sinistra
        for (int c = 0; c < giorni.size(); c++) {
            String giornoStr = java.time.LocalDate.parse(giorni.get(c))
                    .format(FMT_GIORNO);
            Label lbl = new Label(giornoStr);
            lbl.setStyle("-fx-text-fill: #9ba3b8; -fx-font-size: 10; " +
                         "-fx-font-family: Consolas; -fx-alignment: center;");
            lbl.setMinWidth(CELL_W);
            lbl.setAlignment(Pos.CENTER);
            grid.add(lbl, c + 1, 0);
        }

        // Righe: ora 0–23
        for (int ora = 0; ora < 24; ora++) {
            // Etichetta ora (colonna 0)
            Label oraLabel = new Label(String.format("%02d:00", ora));
            oraLabel.setStyle("-fx-text-fill: #5a6278; -fx-font-size: 10; " +
                              "-fx-font-family: Consolas;");
            oraLabel.setMinWidth(38);
            oraLabel.setAlignment(Pos.CENTER_RIGHT);
            grid.add(oraLabel, 0, ora + 1);

            // Celle colorate
            for (int c = 0; c < giorni.size(); c++) {
                String giornoKey = giorni.get(c);
                Map<Integer, List<Double>> oreMap = griglia.getOrDefault(giornoKey, Map.of());
                List<Double> valori = oreMap.getOrDefault(ora, List.of());

                if (valori.isEmpty()) {
                    // Nessun dato → cella grigia scura
                    grid.add(cellaVuota(), c + 1, ora + 1);
                } else {
                    double media = valori.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                    Color colore = interpolaColore(media, minVal, maxVal);
                    String tooltipText = String.format("%s  ore %02d:00\nMedia: %.1f µg/m³\n(%d misurazioni)",
                            java.time.LocalDate.parse(giornoKey).format(FMT_TOOLTIP),
                            ora, media, valori.size());
                    grid.add(cellaColorata(colore, tooltipText), c + 1, ora + 1);
                }
            }
        }

        // 3) Legenda colori
        HBox legenda = costruisciLegenda(minVal, maxVal);

        // 4) Titolo
        Label titolo = new Label("Mappa di calore PM10 — media sensori Brescia");
        titolo.setStyle("-fx-text-fill: #4FC3F7; -fx-font-size: 13; " +
                        "-fx-font-weight: bold; -fx-font-family: Consolas;");

        Label sottotitolo = new Label("Righe = ore del giorno  ·  Colonne = ultimi 7 giorni  " +
                                      "·  Passa il mouse su una cella per i dettagli");
        sottotitolo.setStyle("-fx-text-fill: #5a6278; -fx-font-size: 10; -fx-font-family: Consolas;");

        VBox header = new VBox(4, titolo, sottotitolo);
        header.setPadding(new Insets(0, 0, 12, 0));

        VBox root = new VBox(10, header, grid, legenda);
        root.setAlignment(Pos.TOP_LEFT);

        // Centra nella ScrollPane per schermi piccoli
        ScrollPaneHelper scroll = new ScrollPaneHelper(root);
        container.setCenter(scroll.get());
    }

    // ----------------------------------------------------------------
    // Costruzione celle
    // ----------------------------------------------------------------

    private StackPane cellaColorata(Color colore, String tooltipText) {
        Rectangle rect = new Rectangle(CELL_W, CELL_H);
        rect.setFill(colore);
        rect.setArcWidth(3);
        rect.setArcHeight(3);

        StackPane cell = new StackPane(rect);
        Tooltip tip = new Tooltip(tooltipText);
        tip.setStyle("-fx-font-family: Consolas; -fx-font-size: 11;");
        Tooltip.install(cell, tip);
        return cell;
    }

    private StackPane cellaVuota() {
        Rectangle rect = new Rectangle(CELL_W, CELL_H);
        rect.setFill(Color.web("#1e2330"));
        rect.setArcWidth(3);
        rect.setArcHeight(3);
        return new StackPane(rect);
    }

    private Region emptyCell() {
        Region r = new Region();
        r.setMinWidth(38);
        return r;
    }

    // ----------------------------------------------------------------
    // Colore: verde (basso) → giallo (soglia UE) → rosso (alto)
    // ----------------------------------------------------------------

    /**
     * Mappa un valore numerico su una scala di colori:
     *   0%  → verde scuro  (#2E7D32)
     *   50% → giallo       (#F9A825)  ← circa soglia UE
     *   100%→ rosso        (#B71C1C)
     */
    private Color interpolaColore(double valore, double min, double max) {
        if (max <= min) return Color.web("#2E7D32");
        double t = Math.min(1.0, Math.max(0.0, (valore - min) / (max - min)));

        if (t < 0.5) {
            // verde → giallo
            double u = t * 2;
            return interpola(Color.web("#2E7D32"), Color.web("#F9A825"), u);
        } else {
            // giallo → rosso
            double u = (t - 0.5) * 2;
            return interpola(Color.web("#F9A825"), Color.web("#B71C1C"), u);
        }
    }

    private Color interpola(Color a, Color b, double t) {
        return new Color(
                a.getRed()   + (b.getRed()   - a.getRed())   * t,
                a.getGreen() + (b.getGreen() - a.getGreen()) * t,
                a.getBlue()  + (b.getBlue()  - a.getBlue())  * t,
                1.0
        );
    }

    // ----------------------------------------------------------------
    // Legenda
    // ----------------------------------------------------------------

    private HBox costruisciLegenda(double min, double max) {
        HBox box = new HBox(6);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(8, 0, 0, 40));

        Label lblTitolo = new Label("Legenda:");
        lblTitolo.setStyle("-fx-text-fill: #9ba3b8; -fx-font-size: 10; -fx-font-family: Consolas;");
        box.getChildren().add(lblTitolo);

        // Gradiente continuo fatto di 20 rettangolini
        for (int i = 0; i < 20; i++) {
            double t = i / 19.0;
            double val = min + t * (max - min);
            Color c = interpolaColore(val, min, max);
            Rectangle r = new Rectangle(16, 12);
            r.setFill(c);
            box.getChildren().add(r);
        }

        Label lblMin = new Label(String.format("%.0f µg/m³", min));
        Label lblMax = new Label(String.format("%.0f µg/m³", max));
        Label lblUE  = new Label("  ·  soglia UE: 50 µg/m³");
        lblMin.setStyle("-fx-text-fill: #9ba3b8; -fx-font-size: 10; -fx-font-family: Consolas;");
        lblMax.setStyle("-fx-text-fill: #9ba3b8; -fx-font-size: 10; -fx-font-family: Consolas;");
        lblUE.setStyle("-fx-text-fill: #EF5350; -fx-font-size: 10; -fx-font-family: Consolas;");

        box.getChildren().addAll(lblMin, lblMax, lblUE);
        return box;
    }

    // ----------------------------------------------------------------
    // Helper per la ScrollPane (classe interna statica)
    // ----------------------------------------------------------------

    private static class ScrollPaneHelper {
        private final javafx.scene.control.ScrollPane sp;

        ScrollPaneHelper(javafx.scene.layout.Region content) {
            sp = new javafx.scene.control.ScrollPane(content);
            sp.setFitToWidth(false);
            sp.setFitToHeight(false);
            sp.setStyle("-fx-background-color: #0f1117; -fx-background: #0f1117;");
            sp.setPannable(true);
        }

        javafx.scene.control.ScrollPane get() { return sp; }
    }
}
