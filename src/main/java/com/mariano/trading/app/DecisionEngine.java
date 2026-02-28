package com.mariano.trading.app;

public class DecisionEngine {

    public enum Estado { ENTRAR, VIGILAR, MANTENER, SALIR, EVITAR }

    public static class Decision {
        public int score;        // 0..100
        public Estado estado;    // ENTRAR/VIGILAR/MANTENER/SALIR/EVITAR
        public String resumen;   // 1 línea, ultra clara
        public String reporte;   // reporte completo (secciones)
    }

    public Decision decide(double close, double sma20, double sma50, double rsi14, double atr, boolean inPosition) {
        Decision d = new Decision();

        boolean trendUp = sma20 > sma50;
        boolean trendDown = sma20 < sma50;

        double distSma20Pct = ((close - sma20) / sma20) * 100.0;
        double distSma50Pct = ((close - sma50) / sma50) * 100.0;
        double atrPct = (atr / close) * 100.0;

        // ---------------- SCORE (tu idea original) ----------------
        int score = 50;

        // Tendencia
        if (trendUp) score += 20;
        else if (trendDown) score -= 20;

        // RSI
        if (rsi14 < 30) score += 10;
        else if (rsi14 < 40) score += 5;
        else if (rsi14 > 70) score -= 15;
        else if (rsi14 > 60) score -= 5;

        // Precio vs SMA20
        if (close > sma20) score += 5;
        else score -= 5;

        // Distancia SMA50
        if (distSma50Pct > 5) score -= 5;
        else if (distSma50Pct < -5) score -= 5;

        // Volatilidad
        if (atrPct > 4) score -= 10;
        else if (atrPct > 2.5) score -= 5;

        if (score < 0) score = 0;
        if (score > 100) score = 100;

        // ---------------- REGLAS DE ESTADO (más “trader”) ----------------
        boolean exitSignal = (close < sma50) || (rsi14 < 35 && trendDown);

        // bajista + RSI <= 40 + muy debajo SMA50 => posible rebote, pero NO comprar todavía
        boolean reboundWatch = trendDown && rsi14 <= 40 && distSma50Pct <= -8;

        // Entrada “tendencia + fuerza”
        boolean entrySetup = trendUp && close > sma20 && rsi14 >= 45 && rsi14 <= 65;

        Estado estado;
        if (inPosition) {
            estado = exitSignal ? Estado.SALIR : Estado.MANTENER;
        } else {
            if (entrySetup && score >= 65) estado = Estado.ENTRAR;
            else if (reboundWatch) estado = Estado.VIGILAR;
            else if (score >= 55) estado = Estado.VIGILAR;
            else estado = Estado.EVITAR;
        }

        // ---------------- REPORTE CLARO ----------------
        StringBuilder rep = new StringBuilder();

        rep.append("=== ANÁLISIS TÉCNICO ===\n");

        rep.append("\n[1] Tendencia (SMA20 vs SMA50)\n");
        if (trendUp) rep.append(String.format("- Alcista: SMA20(%.2f) > SMA50(%.2f).\n", sma20, sma50));
        else if (trendDown) rep.append(String.format("- Bajista: SMA20(%.2f) < SMA50(%.2f).\n", sma20, sma50));
        else rep.append(String.format("- Lateral: SMA20(%.2f) ≈ SMA50(%.2f).\n", sma20, sma50));

        rep.append("\n[2] Precio vs medias\n");
        rep.append(String.format("- Precio vs SMA20: %.2f%% (%s)\n",
                distSma20Pct, close > sma20 ? "arriba (fuerza)" : "abajo (debilidad)"));
        rep.append(String.format("- Precio vs SMA50: %.2f%% (%s)\n", distSma50Pct,
                distSma50Pct <= -8 ? "muy por debajo (posible rebote, pero riesgo alto)" :
                        distSma50Pct < 0 ? "por debajo (mercado débil)" :
                                "por encima (mercado fuerte)"));

        rep.append("\n[3] Momento (RSI14)\n");
        if (rsi14 < 30) rep.append(String.format("- RSI %.2f: sobrevendido (rebote posible).\n", rsi14));
        else if (rsi14 < 40) rep.append(String.format("- RSI %.2f: bajo (presión bajista, rebote posible si confirma).\n", rsi14));
        else if (rsi14 > 70) rep.append(String.format("- RSI %.2f: sobrecomprado (riesgo corrección).\n", rsi14));
        else if (rsi14 > 60) rep.append(String.format("- RSI %.2f: alto (podés estar entrando tarde).\n", rsi14));
        else rep.append(String.format("- RSI %.2f: neutral.\n", rsi14));

        rep.append("\n[4] Volatilidad (ATR)\n");
        rep.append(String.format("- ATR%% %.2f%%: %s\n", atrPct,
                atrPct > 4 ? "alta (más riesgo)" : atrPct > 2.5 ? "media" : "baja"));

        // Resumen (1 línea) para conclusión
        String resumen;
        switch (estado) {
            case ENTRAR -> resumen = "ENTRAR: el setup es favorable (tendencia + fuerza).";
            case VIGILAR -> resumen = "VIGILAR: hay señales mixtas; esperar confirmación antes de comprar.";
            case MANTENER -> resumen = "MANTENER: no hay señal clara de salida; mantener con stop/trailing.";
            case SALIR -> resumen = "SALIR: señales de deterioro; cerrar o reducir posición.";
            default -> resumen = "EVITAR: setup flojo/arriesgado por ahora.";
        }

        rep.append("\n=== CONCLUSIÓN ===\n");
        rep.append(String.format("- Score: %d/100\n", score));
        rep.append(String.format("- Estado final: %s\n", estado));
        rep.append("- ").append(resumen).append("\n");

        // Extra: si estás en posición y es SALIR, aclarar por qué
        if (inPosition && estado == Estado.SALIR) {
            rep.append("\n=== SEÑAL DE SALIDA ===\n");
            if (close < sma50) rep.append("- Precio por debajo de SMA50: deterioro de estructura.\n");
            if (rsi14 < 35 && trendDown) rep.append("- RSI muy débil en tendencia bajista.\n");
        }

        // Confirmación sugerida (para pasar de VIGILAR a ENTRAR)
        rep.append("\n=== CONFIRMACIÓN SUGERIDA ===\n");
        if (!inPosition) {
            if (estado == Estado.VIGILAR) {
                rep.append("- Para mejorar la señal, esperaría 1 o 2 de estas confirmaciones:\n");
                rep.append(String.format("  1) Cierre por encima de SMA20 (%.2f).\n", sma20));
                rep.append("  2) RSI vuelva a > 45 (mejor momento).\n");
                rep.append("  3) (Opcional) Romper el máximo del día previo.\n");
            } else if (estado == Estado.ENTRAR) {
                rep.append("- Setup ya confirmado. Aun así: entrar con plan (stop/size).\n");
            } else if (estado == Estado.EVITAR) {
                rep.append("- No hay setup. Para reconsiderar: tendencia menos bajista y precio recuperando SMA20.\n");
            } else {
                rep.append("- Sin confirmación adicional relevante.\n");
            }
        } else {
            rep.append("- Estás en posición: la confirmación clave es mantenerte arriba del stop/trailing.\n");
        }

        d.score = score;
        d.estado = estado;
        d.resumen = resumen;
        d.reporte = rep.toString();
        return d;
    }
}