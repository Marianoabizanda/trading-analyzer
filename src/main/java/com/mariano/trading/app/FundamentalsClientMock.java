package com.mariano.trading.app;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.Map;

public class FundamentalsClientMock {

    private final Map<String, FundamentalData> data;

    public FundamentalsClientMock() {
        try {
            ObjectMapper mapper = new ObjectMapper();

            InputStream is = getClass().getClassLoader().getResourceAsStream("fundamentals.json");
            if (is == null) {
                throw new RuntimeException("No se encontró fundamentals.json en src/main/resources");
            }

            data = mapper.readValue(
                    is,
                    mapper.getTypeFactory().constructMapType(Map.class, String.class, FundamentalData.class)
            );

        } catch (Exception e) {
            throw new RuntimeException("Error cargando fundamentals.json: " + e.getMessage());
        }
    }

    public FundamentalData get(String ticker) {
        return data.get(ticker.toLowerCase());
    }
}