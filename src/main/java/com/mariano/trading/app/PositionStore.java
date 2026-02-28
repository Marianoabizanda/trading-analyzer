package com.mariano.trading.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.LocalDate;

public class PositionStore {

    private final Path file;

    public PositionStore(String filename) {
        this.file = Paths.get(filename);
    }

    public Optional<Position> load() {
        if (!Files.exists(file)) return Optional.empty();

        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);

            Position p = new Position();
            p.ticker = extractString(json, "ticker");
            p.entryPrice = extractDouble(json, "entryPrice");
            p.highestClose = extractDouble(json, "highestClose");
            String dateStr = extractString(json, "entryDate");
            if (dateStr != null) {
                p.entryDate = LocalDate.parse(dateStr);
            }

            if (p.ticker == null || p.entryDate == null) return Optional.empty();
            if (p.entryPrice <= 0) return Optional.empty();

            return Optional.of(p);
        } catch (Exception e) {
            System.out.println("X No se pudo leer position.json: " + e.getMessage());
            return Optional.empty();
        }
    }

    public void save(Position p) {
        String json = "{\n" +
                "  \"ticker\": \"" + escape(p.ticker) + "\",\n" +
                "  \"entryPrice\": " + p.entryPrice + ",\n" +
                "  \"entryDate\": \"" + p.entryDate.toString() + "\",\n" +
                "  \"highestClose\": " + p.highestClose + "\n" +
                "}\n";

        try {
            Files.writeString(file, json, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            System.out.println("X No se pudo guardar position.json: " + e.getMessage());
        }
    }

    public void clear() {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            System.out.println("X No se pudo borrar position.json: " + e.getMessage());
        }
    }

    private static String extractString(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"(.*?)\"");
        Matcher m = pattern.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private static double extractDouble(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*([-0-9.]+)");
        Matcher m = pattern.matcher(json);
        return m.find() ? Double.parseDouble(m.group(1)) : 0.0;
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}