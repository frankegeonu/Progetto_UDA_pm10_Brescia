module com.pm10brescia {
    requires javafx.controls;
    requires javafx.fxml;

    // ============================================================
    // MODULO DA AGGIUNGERE: Apache Commons CSV
    // Serve perché CsvDataService usa org.apache.commons.csv.*
    // ============================================================
    requires org.apache.commons.csv;

    // Apre il package al framework FXML (necessario per il controller)
    opens com.pm10brescia to javafx.fxml;
    exports com.pm10brescia;
}
