# Valori PM10 · Brescia

Applicazione JavaFX per la visualizzazione dei dati PM10 rilevati dai sensori ARPA Lombardia nel comune di Brescia.

## Descrizione

L'applicazione mostra un **grafico a linee interattivo** con l'andamento del PM10 nei tre sensori presenti a Brescia:

- Brescia - Via Ziziola (ID 5504)
- Brescia - Villaggio Sereno (ID 5707)
- Brescia - Broletto (ID 5812)

I dati sono forniti da [ARPA Lombardia](https://www.dati.lombardia.it/Ambiente/Dati-sensori-aria/nicp-bhqi) e inclusi direttamente nel progetto. All'avvio il grafico si popola automaticamente senza nessuna azione da parte dell'utente.

È presente anche una **mappa di calore** (tab dedicata) che mostra la media dei tre sensori per ora del giorno e per giorno, permettendo di identificare visivamente i pattern di inquinamento.

## Requisiti

| Strumento | Versione minima |
|-----------|----------------|
| Java JDK  | 21              |
| Maven     | 3.8+            |

JavaFX e Apache Commons CSV sono inclusi come dipendenze Maven: non serve installarli separatamente.

## Come eseguire

### 1. Clona il repository

```bash
git clone https://github.com/<utente>/pm10-brescia.git
cd pm10-brescia
```

### 2. Apri in IntelliJ

**File → New → Project from Existing Sources** → seleziona la cartella `pm10-brescia` → scegli **Maven** → Finish.

Aspetta che Maven scarichi le dipendenze (barra di avanzamento in basso).

### 3. Avvia

Apri `HelloApplication.java` e premi il triangolo verde ▶ accanto al metodo `main`.

Oppure da terminale:

```bash
mvn javafx:run
```

## Funzionalità

| Funzionalità | Descrizione |
|---|---|
| Grafico a linee | Andamento PM10 dei 3 sensori con linea soglia UE (50 µg/m³) |
| Mappa di calore | Griglia ora × giorno colorata (verde→rosso) con media dei sensori |
| Aggregazione | Dati grezzi · media oraria · media giornaliera |
| Filtro periodo | Ultimi 7 giorni · ultimi 14 giorni · tutto il periodo |
| Statistiche | Media e massimo del periodo selezionato nella barra in basso |

## Struttura del codice

```
src/main/java/com/pm10brescia/
├── HelloApplication.java   — entry point, carica l'FXML e apre la finestra
├── HelloController.java    — controller JavaFX, gestisce filtri e aggiornamento viste
├── HeatMapView.java        — vista mappa di calore (GridPane custom)
├── CsvDataService.java     — lettura e parsing del CSV ARPA Lombardia
└── PM10Reading.java        — model: singola misurazione (timestamp, sensore, valore)

src/main/resources/com/pm10brescia/
├── hello-view.fxml         — layout della finestra (TabPane con grafico e heatmap)
└── pm10_brescia.csv        — dati PM10 Brescia nel formato ARPA Lombardia
```

### Separazione delle responsabilità

```
CsvDataService        →  legge  il CSV
PM10Reading           →  struttura dati (model)
HelloController       →  applica i filtri, coordina le due viste
HelloApplication      →  entry point JavaFX
HeatMapView           →  disegna la mappa di calore
hello-view.fxml       →  definisce il layout della UI
```

## Formato CSV

Il file CSV segue il formato del portale opendata di ARPA Lombardia:

```
"IdSensore","Data","Valore","Stato","idOperatore"
"5504","01/04/2026 08:00:00 AM","48,6","VA","1"
```

Solo le righe con `Stato = VA` (valido) vengono incluse nel grafico.

## Dati

Fonte: [ARPA Lombardia — Dati sensori aria](https://www.dati.lombardia.it/Ambiente/Dati-sensori-aria/nicp-bhqi)

Progetto svolto da: Regio Matteo, Gaburri Samuele e Frank Egeonu
