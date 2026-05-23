package com.pm10brescia;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Gestisce la lettura dei file CSV di ARPA Lombardia e la generazione
 * di dati di esempio. Nessuna dipendenza da JavaFX: testabile in isolamento.
 *
 * Formato CSV atteso (separatore ; oppure ,):
 *   Data        → dd/MM/yyyy
 *   Ora         → HH:mm:ss
 *   NomeSensore → nome stringa del sensore
 *   Valore      → numero decimale (virgola o punto)
 */
public class CsvDataService {

    private int righeScartate = 0;

    /** Numero di righe CSV scartate nell'ultima chiamata a {@link #readCsv}. */
    public int getRigheScartate() { return righeScartate; }

    // Formati data/ora riconosciuti automaticamente
    private static final List<DateTimeFormatter> FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    );

    /**
     * Legge un file CSV e restituisce una mappa:
     *   nome sensore → lista di PM10Reading (già ordinate per timestamp)
     *
     * @param file file CSV da leggere
     * @return mappa sensore → letture
     * @throws IOException se il file non è leggibile
     */
    public Map<String, List<PM10Reading>> readCsv(File file) throws IOException {
        char delimiter = detectDelimiter(file);
        righeScartate = 0;
        Map<String, List<PM10Reading>> result = new LinkedHashMap<>();

        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setDelimiter(delimiter)
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreHeaderCase(true)
                     .setTrim(true)
                     .setIgnoreEmptyLines(true)
                     .build()
                     .parse(reader)) {

            for (CSVRecord record : parser) {
                PM10Reading reading = parseRecord(record);
                if (reading != null) {
                    result.computeIfAbsent(reading.getSensorName(), k -> new ArrayList<>())
                          .add(reading);
                } else {
                    righeScartate++;
                }
            }
        }

        // Ordina ogni serie per timestamp crescente
        result.values().forEach(list -> list.sort(
                Comparator.comparing(PM10Reading::getTimestamp)));

        return result;
    }

    /**
     * Genera 30 giorni di dati simulati per 3 sensori di Brescia.
     * Utile per mostrare l'app senza avere il CSV reale.
     */
    public Map<String, List<PM10Reading>> generateSampleData() {
        Map<String, List<PM10Reading>> result = new LinkedHashMap<>();
        String[] sensors = {
                "Brescia - Via Ziziola",
                "Brescia - Villaggio Sereno",
                "Brescia - Broletto"
        };
        double[] base   = { 35, 28, 42 };
        double[] noise  = { 10,  8, 12 };
        Random rng = new Random(42);
        LocalDateTime start = LocalDateTime.now().minusDays(30).withMinute(0).withSecond(0).withNano(0);

        for (int s = 0; s < sensors.length; s++) {
            List<PM10Reading> list = new ArrayList<>();
            for (int h = 0; h < 30 * 24; h++) {
                LocalDateTime ts = start.plusHours(h);
                int hour = ts.getHour();
                // picco mattutino (7-9) e serale (17-20) per simulare il traffico
                double peak = (hour >= 7 && hour <= 9) || (hour >= 17 && hour <= 20) ? 15 : 0;
                double value = Math.max(2, base[s] + peak + rng.nextGaussian() * noise[s] * 0.4);
                list.add(new PM10Reading(ts, sensors[s], value));
            }
            result.put(sensors[s], list);
        }
        return result;
    }

    // --- helpers privati ---

    private char detectDelimiter(File file) throws IOException {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line = br.readLine();
            if (line == null) return ',';
            long semi  = line.chars().filter(c -> c == ';').count();
            long comma = line.chars().filter(c -> c == ',').count();
            return semi > comma ? ';' : ',';
        }
    }

    private PM10Reading parseRecord(CSVRecord r) {
        String dateStr   = getField(r, "data", "date", "DataRilevazione");
        String timeStr   = getField(r, "ora",  "hour", "Ora");
        String sensor    = getField(r, "nomesensore", "sensore", "stazione", "sensorname");
        String valueStr  = getField(r, "valore", "value", "pm10");

        if (dateStr == null || sensor == null || valueStr == null) return null;

        String dtStr = (timeStr != null && !timeStr.isBlank())
                ? dateStr.trim() + " " + timeStr.trim()
                : dateStr.trim();

        LocalDateTime ts = parseDateTime(dtStr);
        if (ts == null) return null;

        try {
            double val = Double.parseDouble(valueStr.replace(',', '.').trim());
            if (val < 0) return null;
            return new PM10Reading(ts, sensor.trim(), val);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String getField(CSVRecord record, String... names) {
        for (String name : names) {
            try {
                String v = record.get(name);
                if (v != null && !v.isBlank()) return v;
            } catch (IllegalArgumentException ignored) { }
        }
        return null;
    }

    private LocalDateTime parseDateTime(String s) {
        for (DateTimeFormatter fmt : FORMATTERS) {
            try { return LocalDateTime.parse(s, fmt); }
            catch (DateTimeParseException ignored) { }
        }
        return null;
    }
}
