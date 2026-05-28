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
 * Legge il file CSV nel formato ARPA Lombardia:
 *   "IdSensore","Data","Valore","Stato","idOperatore"
 *   "5504","01/04/2026 01:00:00 AM","18,2","VA","1"
 *
 * I sensori sono identificati da IdSensore numerico.
 * La mappa dei nomi (IdSensore → nome leggibile) è definita qui sotto.
 */
public class CsvDataService {

    // Mappa IdSensore → nome leggibile per i 3 sensori di Brescia
    private static final Map<String, String> NOMI_SENSORI = Map.of(
            "5504", "Brescia - Via Ziziola",
            "5707", "Brescia - Villaggio Sereno",
            "5812", "Brescia - Broletto"
    );

    private static final DateTimeFormatter FMT_ARPA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm:ss a", java.util.Locale.ENGLISH);

    /**
     * Legge un InputStream CSV nel formato ARPA Lombardia e restituisce
     * una mappa nome sensore → lista letture ordinate per timestamp.
     */
    public Map<String, List<PM10Reading>> readCsv(InputStream is) throws IOException {
        Map<String, List<PM10Reading>> result = new LinkedHashMap<>();

        try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
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
                }
            }
        }

        result.values().forEach(list ->
                list.sort(Comparator.comparing(PM10Reading::getTimestamp)));

        return result;
    }

    private PM10Reading parseRecord(CSVRecord r) {
        try {
            String idSensore = r.get("IdSensore").replace("\"", "").trim();
            String dataStr   = r.get("Data").replace("\"", "").trim();
            String valoreStr = r.get("Valore").replace("\"", "").trim();
            String stato     = r.get("Stato").replace("\"", "").trim();

            // Scarta le letture non valide
            if (!stato.equalsIgnoreCase("VA")) return null;

            // Risolve il nome del sensore
            String nome = NOMI_SENSORI.getOrDefault(idSensore, "Sensore " + idSensore);

            // Parsa il timestamp
            LocalDateTime ts = LocalDateTime.parse(dataStr, FMT_ARPA);

            // Parsa il valore (virgola come separatore decimale)
            double valore = Double.parseDouble(valoreStr.replace(',', '.'));
            if (valore < 0) return null;

            return new PM10Reading(ts, nome, valore);

        } catch (Exception e) {
            return null;
        }
    }
}
