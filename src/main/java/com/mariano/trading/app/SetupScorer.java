package com.mariano.trading.app;

import java.util.ArrayList;
import java.util.List;

public class SetupScorer {

    public static class SetupResult {
        public String type;                 // "BREAKOUT" / "NONE"
        public int score;                   // 0..100
        public List<String> reasons = new ArrayList<>();
    }

    /**
     * Scoring intuitivo de Breakout.
     * No decide comprar/vender: solo puntúa y explica.
     */
    public SetupResult scoreBreakout(
            double close,
            Double breakoutLevel,   // normalmente High20Prev
            double atr14,
            double rsi14,
            double ema20,
            double ema50,
            double ema200,
            RegimeClassifier.Regime regime
    ) {
        SetupResult r = new SetupResult();

        // Si no tenemos nivel, no podemos evaluar
        if (breakoutLevel == null || breakoutLevel <= 0) {
            r.type = "NONE";
            r.score = 0;
            r.reasons.add("No hay suficientes datos para calcular el nivel de breakout (High20 previo).");
            return r;
        }

        // Detectar breakout real (close > nivel previo)
        boolean isBreakout = close > breakoutLevel;

        if (!isBreakout) {
            r.type = "NONE";
            r.score = 0;
            r.reasons.add(String.format("No hay breakout: el precio (%.2f) está por debajo del nivel clave (%.2f).", close, breakoutLevel));
            r.reasons.add("Idea simple: un breakout suele ser más confiable cuando el precio cierra por arriba del máximo de las últimas semanas.");
            return r;
        }

        r.type = "BREAKOUT";

        // Base score por confirmar breakout
        double score = 55;
        r.reasons.add(String.format("✅ Breakout: cierre (%.2f) por arriba del High20 previo (%.2f).", close, breakoutLevel));

        // 1) Fuerza del breakout (en ATR)
        double atrSafe = (atr14 > 0) ? atr14 : 1.0;
        double strengthAtr = (close - breakoutLevel) / atrSafe;

        if (strengthAtr >= 0.25) {
            score += 12;
            r.reasons.add("✅ Ruptura con fuerza: el cierre quedó claramente por arriba del nivel (≥ 0.25 ATR).");
        } else if (strengthAtr >= 0.10) {
            score += 6;
            r.reasons.add("👍 Ruptura aceptable: el cierre superó el nivel (≥ 0.10 ATR).");
        } else {
            score -= 8;
            r.reasons.add("⚠️ Ruptura débil: el cierre apenas superó el nivel (riesgo de falso breakout).");
        }

        // 2) Tendencia por EMAs (confirmación)
        boolean emaBull = ema20 > ema50 && ema50 > ema200;
        boolean emaMid = ema20 > ema50;

        if (emaBull) {
            score += 15;
            r.reasons.add("✅ Tendencia fuerte: EMA20 > EMA50 > EMA200 (tendencia alcista clara).");
        } else if (emaMid) {
            score += 7;
            r.reasons.add("👍 Tendencia moderada: EMA20 > EMA50 (mejor que nada).");
        } else {
            score -= 12;
            r.reasons.add("⚠️ Tendencia no acompaña: las EMAs no están alineadas al alza.");
        }

        // 3) Régimen (contexto)
        if (regime == RegimeClassifier.Regime.TREND_UP) {
            score += 10;
            r.reasons.add("✅ Contexto favorable: el mercado está en TREND_UP (los breakouts suelen funcionar mejor).");
        } else if (regime == RegimeClassifier.Regime.RANGE) {
            score -= 5;
            r.reasons.add("⚠️ Contexto lateral: RANGE (más probabilidad de falsas rupturas).");
        } else if (regime == RegimeClassifier.Regime.TREND_DOWN) {
            score -= 15;
            r.reasons.add("❌ Contexto en contra: TREND_DOWN (breakout contra tendencia = más riesgo).");
        }

        // 4) RSI (momento)
        if (rsi14 >= 55 && rsi14 <= 70) {
            score += 8;
            r.reasons.add("✅ Momento sano: RSI entre 55 y 70 (fuerza sin estar extremo).");
        } else if (rsi14 < 50) {
            score -= 10;
            r.reasons.add("⚠️ Momento flojo: RSI < 50 (puede faltar fuerza real).");
        } else if (rsi14 > 75) {
            score -= 5;
            r.reasons.add("⚠️ Muy caliente: RSI > 75 (puede venir pullback).");
        }

        // Clamp 0..100
        int finalScore = (int) Math.round(Math.max(0, Math.min(100, score)));
        r.score = finalScore;

        // Conclusión simple para no expertos
        if (finalScore >= 80) {
            r.reasons.add("📌 Lectura simple: breakout fuerte y con contexto a favor. Es de las mejores señales.");
        } else if (finalScore >= 65) {
            r.reasons.add("📌 Lectura simple: señal interesante, pero conviene vigilar confirmación (por ejemplo 1-2 cierres arriba del nivel).");
        } else {
            r.reasons.add("📌 Lectura simple: hay breakout, pero la calidad no es buena (riesgo de falsa señal).");
        }

        return r;
    }
}