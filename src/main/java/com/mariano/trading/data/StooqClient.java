package com.mariano.trading.data;

import com.mariano.trading.model.Candle;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/*Es una clase cuyo único trabajo es:
Ir a Stooq
Traer datos
Convertirlos en List<Candle>*/
public class StooqClient {

    public List<Candle> fetchDailyCandles(String ticker) {
        // Ejemplos: "aapl.us", "msft.us", "spy.us"
        String url = "https://stooq.com/q/d/l/?s=" + ticker.toLowerCase() + "&i=d";

        try (InputStreamReader reader = new InputStreamReader(new URL(url).openStream(), StandardCharsets.UTF_8)) {

            /*Esto hace:
            Leer el texto CSV
            Interpretar la primera línea como encabezado:*/
            Iterable<CSVRecord> records = CSVFormat.DEFAULT
                    .withFirstRecordAsHeader()
                    .parse(reader);
            /*Ahora records representa cada fila del archivo.*/

            /*Creamos la caja donde vamos a guardar las velas.*/
            List<Candle> candles = new ArrayList<>();

            //la recorre: Esto significa:
            //Para cada fila del CSV…
            for (CSVRecord r : records) {
                LocalDate date = LocalDate.parse(r.get("Date"));
                double open = Double.parseDouble(r.get("Open"));
                double high = Double.parseDouble(r.get("High"));
                double low = Double.parseDouble(r.get("Low"));
                double close = Double.parseDouble(r.get("Close"));
                double volume = Double.parseDouble(r.get("Volume"));

                candles.add(new Candle(date, open, high, low, close, volume));
            }

            // Aseguramos orden cronológico: viejo → nuevo
            candles.sort(Comparator.comparing(Candle::getDate));
            return candles;

        } catch (Exception e) {
            throw new RuntimeException("Error al descargar o parsear datos de Stooq: " + e.getMessage(), e);
        }
    }
}

/*Este archivo hace 4 cosas:

Construir URL
Descargar texto
Convertir texto en objetos
Devolver lista*/