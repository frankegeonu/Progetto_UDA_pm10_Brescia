# PM10 Brescia — Valori dei sensori ARPA Lombardia

Applicazione JavaFX che mostra un grafico a linee con l'andamento del PM10
rilevato dai tre sensori ARPA presenti nel comune di Brescia.

---

## Struttura del progetto

```
pm10-brescia/
│
├── pom.xml                                          ← dipendenze Maven
│
├── src/main/java/
│   ├── module-info.java                             ← dichiarazione modulo Java
│   └── com/pm10brescia/
│       ├── HelloApplication.java                    ← entry point (estende Application)
│       ├── HelloController.java                     ← controller legato all'FXML
│       ├── PM10Reading.java                         ← model: singola misurazione
│       └── CsvDataService.java                      ← lettura CSV + dati di esempio
│
├── src/main/resources/com/pm10brescia/
│   └── hello-view.fxml                              ← layout della finestra
│
└── data/
    └── pm10_brescia_esempio.csv                     ← CSV di esempio (formato ARPA)
```

---

## Cosa devo aggiungere per far funzionare il progetto

### 1. Dipendenza Maven: Apache Commons CSV

Nel `pom.xml` è già inclusa. Serve perché `CsvDataService.java` usa
`org.apache.commons.csv.*` per leggere i file CSV.

Se la vedi evidenziata in rosso in IntelliJ, fai clic su
**"Load Maven Changes"** (l'icona 🔄 in alto a destra quando apri il `pom.xml`).

```xml
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-csv</artifactId>
    <version>1.10.0</version>
</dependency>
```

### 2. SDK Java 21 in IntelliJ

1. **File → Project Structure → Project**
2. **SDK** → seleziona un JDK 21 (se non ce l'hai: **Download JDK → Corretto 21**)
3. **Language level** → 21

### 3. module-info.java

Il file `src/main/java/module-info.java` dichiara il modulo e le sue dipendenze.
Non modificarlo a meno che tu non aggiunga nuove librerie.

---

## Come avviare

```bash
# Dalla cartella del progetto
mvn javafx:run
```

Oppure in IntelliJ: apri `HelloApplication.java` → tasto ▶ verde accanto al `main`.

---

## Come funziona l'app

### Pulsante "Carica CSV"
Apre un file chooser. Seleziona un CSV scaricato da ARPA Lombardia:
[https://www.dati.lombardia.it/Ambiente/Dati-sensori-aria/nicp-bhqj](https://www.dati.lombardia.it/Ambiente/Dati-sensori-aria/nicp-bhqj)

Il parser riconosce automaticamente il separatore (`,` o `;`) e le colonne
`Data`, `Ora`, `NomeSensore`, `Valore`.

### Pulsante "Dati di Esempio"
Genera 30 giorni di dati simulati per 3 sensori senza bisogno di nessun file.

### Filtri
- **Aggregazione**: dati grezzi · media oraria · media giornaliera
- **Periodo**: 7 / 14 / 30 giorni · tutto il dataset
- Premi **Applica** per aggiornare il grafico

La **linea rossa** indica la soglia giornaliera UE per PM10 (50 µg/m³).

---

## File CSV di esempio

La cartella `data/` contiene `pm10_brescia_esempio.csv` con il formato esatto
atteso dall'applicazione. Puoi usarlo come riferimento per capire come ARPA
struttura i dati reali.

```
Data;Ora;NomeSensore;Valore
01/04/2026;08:00:00;Brescia - Via Ziziola;62,1
...
```
