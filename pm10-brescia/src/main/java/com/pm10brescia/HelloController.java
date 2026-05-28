package com.pm10brescia;

import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Controller collegato a hello-view.fxml.
 * All'avvio carica automaticamente il CSV incluso nel progetto.
 */
public class HelloController {

    private static final double LIMITE_UE = 50.0;

    // ----------------------------------------------------------------
    // Elementi iniettati dall'FXML
    // ----------------------------------------------------------------

    @FXML private LineChart<String, Number> lineChart;
    @FXML private ComboBox<String>          comboAggregazione;
    @FXML private ComboBox<String>          comboPeriodo;
    @FXML private Label                     labelStatus;
    @FXML private Label                     labelStats;
    @FXML private ProgressIndicator         progressIndicator;
    @FXML private BorderPane                heatMapContainer;

    // ----------------------------------------------------------------
    // Stato interno
    // ----------------------------------------------------------------

    private final CsvDataService csvService  = new CsvDataService();
    private final HeatMapView    heatMapView = new HeatMapView();

    private Map<String, List<PM10Reading>> datiCorrente = new LinkedHashMap<>();

    private final DateTimeFormatter fmtOra    = DateTimeFormatter.ofPattern("dd/MM HH:mm");
    private final DateTimeFormatter fmtGiorno = DateTimeFormatter.ofPattern("dd/MM");

    // ----------------------------------------------------------------
    // Inizializzazione: carica il CSV automaticamente
    // ----------------------------------------------------------------

    @FXML
    public void initialize() {
        comboAggregazione.setItems(FXCollections.observableArrayList(
                "Dati grezzi", "Media oraria", "Media giornaliera"
        ));
        comboAggregazione.setValue("Media oraria");

        comboPeriodo.setItems(FXCollections.observableArrayList(
                "Ultimi 7 giorni", "Ultimi 14 giorni", "Tutto il periodo"
        ));
        comboPeriodo.setValue("Tutto il periodo");

        lineChart.setAnimated(false);
        lineChart.setCreateSymbols(false);
        progressIndicator.setVisible(false);

        heatMapContainer.setCenter(heatMapView.getContainer());

        // Carica il CSV incluso nel progetto in background
        caricaCsvInterno();
    }

    // ----------------------------------------------------------------
    // Caricamento CSV interno (dalla cartella resources)
    // ----------------------------------------------------------------

    private void caricaCsvInterno() {
        setLoading(true);
        labelStatus.setText("Caricamento dati PM10 Brescia...");

        Task<Map<String, List<PM10Reading>>> task = new Task<>() {
            @Override
            protected Map<String, List<PM10Reading>> call() throws Exception {
                InputStream is = HelloController.class
                        .getResourceAsStream("/com/pm10brescia/pm10_brescia.csv");
                if (is == null) throw new Exception("File CSV non trovato nelle risorse.");
                return csvService.readCsv(is);
            }
        };

        task.setOnSucceeded(e -> {
            datiCorrente = task.getValue();
            setLoading(false);
            applicaFiltri();
            labelStatus.setText("Dati PM10 Brescia caricati — 3 sensori ARPA");
        });

        task.setOnFailed(e -> {
            setLoading(false);
            labelStatus.setText("Errore: " + task.getException().getMessage());
        });

        new Thread(task, "csv-loader").start();
    }

    // ----------------------------------------------------------------
    // Azioni pulsanti
    // ----------------------------------------------------------------

    @FXML
    protected void onApplicaFiltri() {
        applicaFiltri();
    }

    // ----------------------------------------------------------------
    // Logica filtri
    // ----------------------------------------------------------------

    private void applicaFiltri() {
        if (datiCorrente.isEmpty()) return;

        LocalDateTime fineDataset = datiCorrente.values().stream()
                .flatMap(List::stream)
                .map(PM10Reading::getTimestamp)
                .max(Comparator.naturalOrder())
                .orElse(LocalDateTime.now());

        LocalDateTime inizioPeriodo = switch (comboPeriodo.getValue()) {
            case "Ultimi 7 giorni"  -> fineDataset.minusDays(7);
            case "Ultimi 14 giorni" -> fineDataset.minusDays(14);
            default                 -> LocalDateTime.MIN;
        };

        Map<String, List<PM10Reading>> filtrati = new LinkedHashMap<>();
        int totPunti = 0;
        for (Map.Entry<String, List<PM10Reading>> entry : datiCorrente.entrySet()) {
            List<PM10Reading> inRange = entry.getValue().stream()
                    .filter(r -> !r.getTimestamp().isBefore(inizioPeriodo))
                    .toList();

            List<PM10Reading> elaborati = switch (comboAggregazione.getValue()) {
                case "Media oraria"      -> aggregaOra(inRange, entry.getKey());
                case "Media giornaliera" -> aggregaGiorno(inRange, entry.getKey());
                default                  -> inRange;
            };
            filtrati.put(entry.getKey(), elaborati);
            totPunti += elaborati.size();
        }

        aggiornaGrafico(filtrati);

        // Per la heatmap usa sempre i dati grezzi filtrati per range
        Map<String, List<PM10Reading>> perHeatmap = new LinkedHashMap<>();
        for (Map.Entry<String, List<PM10Reading>> entry : datiCorrente.entrySet()) {
            perHeatmap.put(entry.getKey(), entry.getValue().stream()
                    .filter(r -> !r.getTimestamp().isBefore(inizioPeriodo))
                    .toList());
        }
        heatMapView.aggiorna(perHeatmap);

        OptionalDouble media = filtrati.values().stream()
                .flatMapToDouble(l -> l.stream().mapToDouble(PM10Reading::getValue))
                .average();
        OptionalDouble max = filtrati.values().stream()
                .flatMapToDouble(l -> l.stream().mapToDouble(PM10Reading::getValue))
                .max();
        labelStats.setText(String.format("%d punti  |  media %.1f µg/m³  |  max %.1f µg/m³",
                totPunti, media.orElse(0), max.orElse(0)));
    }

    private void aggiornaGrafico(Map<String, List<PM10Reading>> dati) {
        lineChart.getData().clear();

        boolean conOra = dati.values().stream().flatMap(List::stream)
                .anyMatch(r -> r.getTimestamp().getHour() != 0);
        DateTimeFormatter fmt = conOra ? fmtOra : fmtGiorno;

        for (Map.Entry<String, List<PM10Reading>> entry : dati.entrySet()) {
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName(entry.getKey());
            List<PM10Reading> punti = downsample(entry.getValue(), 400);
            for (PM10Reading r : punti) {
                series.getData().add(new XYChart.Data<>(r.getTimestamp().format(fmt), r.getValue()));
            }
            lineChart.getData().add(series);
        }

        // Linea soglia UE
        List<String> ts = lineChart.getData().stream()
                .filter(s -> !s.getData().isEmpty())
                .findFirst()
                .map(s -> List.of(
                        s.getData().get(0).getXValue(),
                        s.getData().get(s.getData().size() - 1).getXValue()))
                .orElse(List.of());

        if (ts.size() == 2) {
            XYChart.Series<String, Number> limite = new XYChart.Series<>();
            limite.setName("Soglia UE 50 µg/m³");
            limite.getData().add(new XYChart.Data<>(ts.get(0), LIMITE_UE));
            limite.getData().add(new XYChart.Data<>(ts.get(1), LIMITE_UE));
            lineChart.getData().add(limite);
        }
    }

    // ----------------------------------------------------------------
    // Aggregazioni
    // ----------------------------------------------------------------

    private List<PM10Reading> aggregaOra(List<PM10Reading> letture, String sensore) {
        Map<String, DoubleSummaryStatistics> gruppi = new TreeMap<>();
        Map<String, LocalDateTime> ts = new TreeMap<>();
        for (PM10Reading r : letture) {
            String key = r.getTimestamp().toLocalDate() + "T"
                    + String.format("%02d", r.getTimestamp().getHour());
            gruppi.computeIfAbsent(key, k -> new DoubleSummaryStatistics()).accept(r.getValue());
            ts.putIfAbsent(key, r.getTimestamp().withMinute(0).withSecond(0).withNano(0));
        }
        return gruppi.entrySet().stream()
                .map(e -> new PM10Reading(ts.get(e.getKey()), sensore, e.getValue().getAverage()))
                .toList();
    }

    private List<PM10Reading> aggregaGiorno(List<PM10Reading> letture, String sensore) {
        Map<String, DoubleSummaryStatistics> gruppi = new TreeMap<>();
        Map<String, LocalDateTime> ts = new TreeMap<>();
        for (PM10Reading r : letture) {
            String key = r.getTimestamp().toLocalDate().toString();
            gruppi.computeIfAbsent(key, k -> new DoubleSummaryStatistics()).accept(r.getValue());
            ts.putIfAbsent(key, r.getTimestamp().withHour(12).withMinute(0).withSecond(0).withNano(0));
        }
        return gruppi.entrySet().stream()
                .map(e -> new PM10Reading(ts.get(e.getKey()), sensore, e.getValue().getAverage()))
                .toList();
    }

    private List<PM10Reading> downsample(List<PM10Reading> list, int max) {
        if (list.size() <= max) return list;
        List<PM10Reading> out = new ArrayList<>(max);
        double step = (double) list.size() / max;
        for (int i = 0; i < max; i++) out.add(list.get((int) (i * step)));
        return out;
    }

    private void setLoading(boolean loading) {
        progressIndicator.setVisible(loading);
    }
}
