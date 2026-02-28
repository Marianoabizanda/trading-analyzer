package com.mariano.trading.app;

import com.mariano.trading.data.StooqClient;
import com.mariano.trading.indicators.ATR;
import com.mariano.trading.indicators.RSI;
import com.mariano.trading.indicators.SMA;
import com.mariano.trading.model.Candle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;

public class Main {

    public static void main(String[] args) {

        // Flags:
        // --full => imprime reporte completo + decisión clara tipo trader por ticker
        boolean full = false;

        List<String> tickersList = new ArrayList<>();
        for (String a : args) {
            if (a == null) continue;
            String s = a.trim();
            if (s.isEmpty()) continue;
            if (s.equalsIgnoreCase("--full")) full = true;
            else tickersList.add(s);
        }

        // Tickers por args (si no pasan nada, usa msft.us)
        String[] tickers = tickersList.isEmpty() ? new String[]{"msft.us"} : tickersList.toArray(new String[0]);

        // Parámetros de riesgo
        double capital = 1_000_000;
        double riskPct = 0.01;

        // Stops ATR
        double initialAtrMult = 2.0;
        double trailingAtrMult = 2.0;

        // Ranking Top N
        //int topN = Math.min(10, tickers.length);

        // Carpeta posiciones
        ensureDir("positions");

        List<ScanResult> results = new ArrayList<>();

        FundamentalsClientMock fundClient = new FundamentalsClientMock();
        FundamentalScorer fundScorer = new FundamentalScorer();

        for (String t : tickers) {
            String ticker = t.toLowerCase().trim();
            if (ticker.isEmpty()) continue;

            try {
                ScanResult r = runAnalysisForTicker(
                        ticker,
                        capital,
                        riskPct,
                        initialAtrMult,
                        trailingAtrMult,
                        full,
                        fundClient,
                        fundScorer
                );
                if (r != null) results.add(r);

                System.out.println("--------------------------------------------------\n");
            } catch (Exception e) {
                System.out.println("X Error analizando " + ticker + ": " + e.getMessage());
                System.out.println("--------------------------------------------------\n");
            }
        }

        // Ranking (ENTRAR > SALIR > VIGILAR > MANTENER > EVITAR) y luego score
        results.sort(Comparator
                .comparingInt((ScanResult r) -> estadoPriority(r.estado)).reversed()
                .thenComparingInt(r -> r.finalScore).reversed()
        );

        int topN = Math.min(10, results.size());

        System.out.println("\n========================================");
        System.out.println("RANKING (TOP " + topN + ")");
        System.out.println("========================================");

        for (int i = 0; i < Math.min(topN, results.size()); i++) {
            ScanResult r = results.get(i);
            System.out.printf(
                    "%2d) %-10s | %-8s | FinalScore %3d | Close %.2f | %s%n",
                    (i + 1),
                    r.ticker.toUpperCase(),
                    r.estado,
                    r.finalScore,
                    r.close,
                    r.resumen
            );
        }

        if (results.isEmpty()) {
            System.out.println("No hubo resultados (¿tickers inválidos o sin datos?).");
        }
    }

    private static ScanResult runAnalysisForTicker(
            String ticker,
            double capital,
            double riskPct,
            double initialAtrMult,
            double trailingAtrMult,
            boolean full,
            FundamentalsClientMock fundClient,
            FundamentalScorer fundScorer
    ) {
        // position por ticker
        PositionStore store = new PositionStore("positions/" + ticker + ".json");

        // 1) Datos
        StooqClient client = new StooqClient();
        List<Candle> candles = client.fetchDailyCandles(ticker);

        if (candles == null || candles.isEmpty()) {
            System.out.println("X No se pudieron obtener datos para: " + ticker);
            return null;
        }

        // 2) Indicadores
        SMA sma = new SMA();
        List<Double> sma20 = sma.calculate(candles, 20);
        List<Double> sma50 = sma.calculate(candles, 50);

        RSI rsiCalc = new RSI();
        List<Double> rsi14 = rsiCalc.calculate(candles, 14);

        ATR atrCalc = new ATR();
        List<Double> atr14 = atrCalc.calculate(candles, 14);

        // ===== BACKTEST MÍNIMO (sin stop, usando BUY/SELL del engine) =====
        List<Double> closes = candles.stream().map(Candle::getClose).toList();

        SignalGenerator sg = new SignalGenerator();
        List<Signal> signals = sg.fromDecisions(closes, sma20, sma50, rsi14, atr14);

        Backtester bt = new Backtester();
        Backtester.Result br = bt.run(candles, signals);

        System.out.printf(
                "BACKTEST (mínimo) | Trades=%d | WinRate=%.1f%% | Return=%.1f%%%n",
                br.trades, br.winRate, br.totalReturnPct
        );

        // 3) Último día
        int last = candles.size() - 1;
        Candle lastCandle = candles.get(last);

        double close = lastCandle.getClose();
        Double lastSma20 = sma20.get(last);
        Double lastSma50 = sma50.get(last);
        Double lastRsi14 = rsi14.get(last);
        Double lastAtr14 = atr14.get(last);

        if (lastSma20 == null || lastSma50 == null || lastRsi14 == null || lastAtr14 == null) {
            System.out.println("X No hay suficientes datos para indicadores (último día) en: " + ticker);
            return null;
        }

        // 4) Posición
        Optional<Position> posOpt = store.load();
        boolean inPosition = posOpt.isPresent() && ticker.equalsIgnoreCase(posOpt.get().ticker);

        double entryPrice = inPosition ? posOpt.get().entryPrice : 0.0;
        double highestClose = inPosition ? posOpt.get().highestClose : 0.0;
        LocalDate entryDate = inPosition ? posOpt.get().entryDate : null;

        // 5) Decisión
        DecisionEngine engine = new DecisionEngine();
        DecisionEngine.Decision decision =
                engine.decide(close, lastSma20, lastSma50, lastRsi14, lastAtr14, inPosition);

        // === FUNDAMENTALS ===

        FundamentalData f = fundClient.get(ticker);
        int fundScore = fundScorer.score(f);

        // Mezcla 65% técnico + 35% fundamental
        int finalScore = (int) Math.round(
                decision.score * 0.65 +
                        fundScore * 0.35
        );

        // 6) Prints
        if (!full) {
            // Modo scanner: corto
            System.out.println("Ticker: " + ticker.toUpperCase() + " | Fecha: " + lastCandle.getDate());
            System.out.printf("Close: %.2f | SMA20: %.2f | SMA50: %.2f | RSI14: %.2f | ATR14: %.2f%n",
                    close, lastSma20, lastSma50, lastRsi14, lastAtr14);
            System.out.println("Estado: " + decision.estado + " | Score: " + decision.score + "/100");
            System.out.println("TechScore: " + decision.score +
                    " | FundScore: " + fundScore +
                    " | FinalScore: " + finalScore);
            System.out.println("Resumen: " + decision.resumen);
        } else {
            // Modo full: trader pro
            System.out.println("========================================");
            System.out.println("TRADING ANALYZER (FULL)");
            System.out.println("========================================");
            System.out.println("Ticker: " + ticker.toUpperCase());
            System.out.println("Fecha: " + lastCandle.getDate());
            System.out.printf("Close: %.2f%n", close);
            System.out.printf("SMA20: %.2f%n", lastSma20);
            System.out.printf("SMA50: %.2f%n", lastSma50);
            System.out.printf("RSI14: %.2f%n", lastRsi14);
            System.out.printf("ATR14: %.2f%n", lastAtr14);

            System.out.println("\n" + decision.reporte);
            System.out.println("\nRESUMEN FINAL: " + decision.resumen);

            // Bloque “decisión clara” tipo trader
            System.out.println("\n========================================");
            System.out.println("DECISIÓN FINAL (CLARA)");
            System.out.println("========================================");
            if (!inPosition) {
                if (decision.estado == DecisionEngine.Estado.ENTRAR) {
                    System.out.println("✅ COMPRAR / ENTRAR (con plan).");
                } else if (decision.estado == DecisionEngine.Estado.VIGILAR) {
                    System.out.println("🟡 NO COMPRAR HOY. VIGILAR para posible entrada.");
                    System.out.println("Condiciones para entrar (resumen):");
                    System.out.println("- Cierre arriba de SMA20");
                    System.out.println("- RSI > 45");
                    System.out.println("- (Opcional) Romper el máximo del día previo");
                } else {
                    System.out.println("⛔ NO COMPRAR. EVITAR por ahora.");
                }
            } else {
                if (decision.estado == DecisionEngine.Estado.SALIR) {
                    System.out.println("🔴 VENDER / SALIR (señal de salida).");
                } else {
                    System.out.println("🟢 MANTENER (sin señal fuerte de salida).");
                }
            }
        }

        // 7) Gestión / riesgo
        RiskManager rm = new RiskManager();

        if (!inPosition) {
            if (decision.estado == DecisionEngine.Estado.ENTRAR) {
                Position p = new Position();
                p.ticker = ticker;
                p.entryPrice = close;
                p.entryDate = lastCandle.getDate();
                p.highestClose = close;
                store.save(p);

                RiskManager.RiskPlan plan = rm.buildPlan(close, lastAtr14, initialAtrMult, capital, riskPct);

                System.out.println(">>> ACCIÓN: ABRIR POSICIÓN (simulada) <<<");
                System.out.printf("Entry %.2f | Stop %.2f | TP %.2f | Size %d%n",
                        close, plan.stopLoss, plan.takeProfit, plan.positionSizeShares);

            } else if (decision.estado == DecisionEngine.Estado.VIGILAR) {
                RiskManager.RiskPlan plan = rm.buildPlan(close, lastAtr14, initialAtrMult, capital, riskPct);
                System.out.printf("Plan hipotético: Entry %.2f | Stop %.2f | TP %.2f | Size %d%n",
                        close, plan.stopLoss, plan.takeProfit, plan.positionSizeShares);
                System.out.println("Acción: NO COMPRAR HOY (VIGILAR).");
            } else {
                System.out.println("Acción: NO COMPRAR (EVITAR).");
            }

        } else {
            if (close > highestClose) highestClose = close;

            RiskManager.RiskPlan openPlan = rm.buildOpenPositionPlan(
                    entryPrice, close, lastAtr14, initialAtrMult, highestClose, trailingAtrMult, capital, riskPct
            );

            boolean stopHit = close <= openPlan.stopLoss;
            boolean engineExit = decision.estado == DecisionEngine.Estado.SALIR;

            System.out.printf("Posición: Entry %.2f (%s) | High %.2f | StopFinal %.2f%n",
                    entryPrice, entryDate, highestClose, openPlan.stopLoss);

            if (stopHit || engineExit) {
                System.out.println(">>> ACCIÓN: CERRAR / VENDER <<<");
                if (stopHit) System.out.println("- Motivo: precio <= stop final.");
                if (engineExit) System.out.println("- Motivo: DecisionEngine SALIR.");
                store.clear();
                System.out.println("Posición cerrada: borrado positions/" + ticker + ".json");
            } else {
                Position updated = new Position();
                updated.ticker = ticker;
                updated.entryPrice = entryPrice;
                updated.entryDate = entryDate;
                updated.highestClose = highestClose;
                store.save(updated);

                System.out.println(">>> ACCIÓN: MANTENER <<<");
            }
        }

        // Resultado ranking
        ScanResult r = new ScanResult();
        r.ticker = ticker;
        r.score = decision.score;
        r.finalScore = finalScore;
        r.estado = decision.estado;
        r.resumen = decision.resumen;
        r.close = close;
        return r;
    }

    private static int estadoPriority(DecisionEngine.Estado e) {
        if (e == null) return 0;
        return switch (e) {
            case ENTRAR -> 5;
            case SALIR -> 4;
            case VIGILAR -> 3;
            case MANTENER -> 2;
            case EVITAR -> 1;
        };
    }

    private static void ensureDir(String dir) {
        try {
            Files.createDirectories(Path.of(dir));
        } catch (IOException e) {
            System.out.println("X No se pudo crear carpeta: " + dir + " -> " + e.getMessage());
        }
    }

    public static class ScanResult {
        public String ticker;
        public int score;
        public DecisionEngine.Estado estado;
        public String resumen;
        public double close;
        public int finalScore;
    }
}