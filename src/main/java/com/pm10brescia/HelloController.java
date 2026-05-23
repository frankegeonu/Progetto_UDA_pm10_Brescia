package com.pm10brescia;

import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Controller collegato a hello-view.fxml tramite l'attributo fx:controller.
 *
 * I campi annotati con @FXML corrispondono agli fx:id definiti nel file FXML.
 * JavaFX li inietta automaticamente all'avvio: non serve istanziarli a mano.
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

    // Contenitore della heatmap (fx:id="heatMapContainer") —
    // HeatMapView ci inserisce il suo BorderPane dentro
    @FXML private javafx.scene.layout.BorderPane heatMapContainer;

    // ----------------------------------------------------------------
    // Stato interno
    // ----------------------------------------------------------------

    private final CsvDataService csvService  = new CsvDataService();
    private final HeatMapView    heatMapView = new HeatMapView();

    private Map<String, List<PM10Reading>> datiCorrente = new LinkedHashMap<>();

    private final DateTimeFormatter fmtOra    = DateTimeFormatter.ofPattern("dd/MM HH:mm");
    private final DateTimeFormatter fmtGiorno = DateTimeFormatter.ofPattern("dd/MM");

    // ----------------------------------------------------------------
    // Inizializzazione
    // ----------------------------------------------------------------

    @FXML
    public void initialize() {
        comboAggregazione.setItems(FXCollections.observableArrayList(
                "Dati grezzi", "Media oraria", "Media giornaliera"
        ));
        comboAggregazione.setValue("Media oraria");

        comboPeriodo.setItems(FXCollections.observableArrayList(
                "Ultimi 7 giorni", "Ultimi 14 giorni", "Ultimo mese", "Tutto il periodo"
        ));
        comboPeriodo.setValue("Ultimi 7 giorni");

        lineChart.setAnimated(false);
        lineChart.setCreateSymbols(false);
        progressIndicator.setVisible(false);

        // Monta il contenuto della HeatMapView nel placeholder dell'FXML
        heatMapContainer.setCenter(heatMapView.getContainer());
    }

    // ----------------------------------------------------------------
    // Azioni pulsanti
    // ----------------------------------------------------------------

    @FXML
    protected void onCaricaCsv() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Seleziona file CSV ARPA Lombardia");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("File CSV", "*.csv", "*.CSV"));
        File file = fc.showOpenDialog(lineChart.getScene().getWindow());
        if (file == null) return;

        setLoading(true);
        labelStatus.setText("Caricamento: " + file.getName() + " …");

        Task<Map<String, List<PM10Reading>>> task = new Task<>() {
            @Override protected Map<String, List<PM10Reading>> call() throws Exception {
                return csvService.readCsv(file);
            }
        };
        task.setOnSucceeded(e -> {
            datiCorrente = task.getValue();
            setLoading(false);
            applicaFiltri();
            int scartate = csvService.getRigheScartate();
            String stato = "File caricato: " + file.getName();
            if (scartate > 0)
                stato += "  ⚠ " + scartate + " righe non valide ignorate";
            labelStatus.setText(stato);
        });
        task.setOnFailed(e -> {
            setLoading(false);
            labelStatus.setText("Errore: " + task.getException().getMessage());
            new Alert(Alert.AlertType.ERROR, "Impossibile leggere il file:\n"
                    + task.getException().getMessage()).showAndWait();
        });
        new Thread(task, "csv-loader").start();
    }

    @FXML
    protected void onDatiEsempio() {
        setLoading(true);
        Task<Map<String, List<PM10Reading>>> task = new Task<>() {
            @Override protected Map<String, List<PM10Reading>> call() {
                return csvService.generateSampleData();
            }
        };
        task.setOnSucceeded(e -> {
            datiCorrente = task.getValue();
            setLoading(false);
            applicaFiltri();
            labelStatus.setText("Dati di esempio caricati (30 giorni, 3 sensori)");
        });
        new Thread(task, "sample-loader").start();
    }

    @FXML
    protected void onApplicaFiltri() {
        applicaFiltri();
    }

    // ----------------------------------------------------------------
    // Logica filtri: aggiorna sia il grafico sia la heatmap
    // ----------------------------------------------------------------

    private void applicaFiltri() {
        if (datiCorrente.isEmpty()) {
            labelStatus.setText("Nessun dato. Carica un CSV o usa i dati di esempio.");
            return;
        }

        // Range temporale
        LocalDateTime fineDataset = datiCorrente.values().stream()
                .flatMap(List::stream)
                .map(PM10Reading::getTimestamp)
                .max(Comparator.naturalOrder())
                .orElse(LocalDateTime.now());

        LocalDateTime inizioPeriodo = switch (comboPeriodo.getValue()) {
            case "Ultimi 7 giorni"  -> fineDataset.minusDays(7);
            case "Ultimi 14 giorni" -> fineDataset.minusDays(14);
            case "Ultimo mese"      -> fineDataset.minusDays(30);
            default                 -> LocalDateTime.MIN;
        };

        // Filtra e aggrega
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

        // Aggiorna grafico a linee
        aggiornaGrafico(filtrati);

        // Aggiorna heatmap (usa sempre i dati grezzi filtrati per range,
        // così la griglia ora×giorno ha senso indipendentemente dall'aggregazione)
        Map<String, List<PM10Reading>> perHeatmap = new LinkedHashMap<>();
        for (Map.Entry<String, List<PM10Reading>> entry : datiCorrente.entrySet()) {
            perHeatmap.put(entry.getKey(), entry.getValue().stream()
                    .filter(r -> !r.getTimestamp().isBefore(inizioPeriodo))
                    .toList());
        }
        heatMapView.aggiorna(perHeatmap);

        // Statistiche
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
