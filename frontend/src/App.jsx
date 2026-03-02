import { useEffect, useState } from "react";
import { analyzeTicker, scanTickers, historyTicker } from "./api";
import { LineChart, Line, ResponsiveContainer, Tooltip, YAxis, XAxis } from "recharts";
import "./App.css";


export default function App() {
  const [tickers, setTickers] = useState(() => localStorage.getItem("tickers") ?? "");
  const [rows, setRows] = useState([]);
  const [selected, setSelected] = useState(null);
  const [detail, setDetail] = useState(null);
  const [history, setHistory] = useState([]);
  const [loadingHistory, setLoadingHistory] = useState(false);

  const [loadingScan, setLoadingScan] = useState(false);
  const [loadingDetail, setLoadingDetail] = useState(false);
  const [error, setError] = useState("");
  const [search, setSearch] = useState("");
  const [estadoFilter, setEstadoFilter] = useState("ALL");
  const [rangeDays, setRangeDays] = useState(30);

  // Guarda automáticamente los tickers
    useEffect(() => {
      localStorage.setItem("tickers", tickers);
    }, [tickers]);

    useEffect(() => {
      if (selected) {
        openDetail(selected);
      }
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [rangeDays]);

  async function runScan() {
    try {
      setError("");
      setLoadingScan(true);

      const fallback = "msft.us,aapl.us,amzn.us,goog.us,meta.us,nvda.us,tsla.us,spy.us,qqq.us,ko.us";
      const clean = tickers
        .split(",")
        .map(t => t.trim())
        .filter(Boolean)
        .join(",");

      const tickersToUse = clean.length ? clean : fallback;

      const data = await scanTickers(tickersToUse);
      setRows(data);
      // reset filtros para que no “oculten” los resultados
      setSearch("");
      setSelected(null);
      setDetail(null);
    } catch (e) {
      setError(e?.message ?? "Error");
    } finally {
      setLoadingScan(false);
    }
  }

 async function openDetail(ticker) {
   try {
     setError("");
     setSelected(ticker);
     setLoadingDetail(true);
     setLoadingHistory(true);
     setHistory([]);

     const data = await analyzeTicker(ticker);
     setDetail(data);

     const buffer = 60; // para que SMA50 tenga “historia”
     const raw = await historyTicker(ticker, rangeDays + buffer);

     const with20 = addSMA(raw, 20, "sma20");
     const with50 = addSMA(with20, 50, "sma50");

     // mostramos solo lo último según rango
     const sliced = with50.slice(Math.max(0, with50.length - rangeDays));
     setHistory(sliced);
   } catch (e) {
     setError(e?.message ?? "Error");
   } finally {
     setLoadingDetail(false);
     setLoadingHistory(false);
   }
 }

  const filteredRows = rows.filter((r) => {
    const t = String(r.ticker ?? "").toLowerCase();
    const s = search.trim().toLowerCase();

    const matchSearch = !s || t.includes(s);
    const matchEstado = estadoFilter === "ALL" || String(r.estado ?? "").toUpperCase() === estadoFilter;

    return matchSearch && matchEstado;
  });

  useEffect(() => {
    runScan();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div className="page">
      <header className="header">
        <h1>Trading Analyzer</h1>
        <p>Ranking (/api/scan) + Detalle (/api/analyze)</p>
      </header>

      <section className="controls">
        <div className="controlsRow">
          <label className="field">
            <span>Tickers (CSV)</span>
            <input
              value={tickers}
              onChange={(e) => setTickers(e.target.value)}
              placeholder="msft.us,aapl.us,amzn.us..."
            />
          </label>

          <label className="field">
            <span>Buscar (ticker)</span>
            <input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="ej: aapl"
            />
          </label>

          <label className="field fieldSmall">
            <span>Historial</span>
            <select value={rangeDays} onChange={(e) => setRangeDays(Number(e.target.value))}>
              <option value={15}>15 días</option>
              <option value={30}>30 días</option>
              <option value={60}>60 días</option>
            </select>
          </label>

          <label className="field fieldSmall">
            <span>Estado</span>
            <select value={estadoFilter} onChange={(e) => setEstadoFilter(e.target.value)}>
              <option value="ALL">Todos</option>
              <option value="ENTRAR">ENTRAR</option>
              <option value="VIGILAR">VIGILAR</option>
              <option value="SALIR">SALIR</option>
            </select>
          </label>

          <button className="primaryBtn" onClick={runScan} disabled={loadingScan}>
            {loadingScan ? "Escaneando..." : "Scan TOP 10"}
          </button>
        </div>
      </section>

      {error && <div className="error">⚠ {error}</div>}

      <main className="grid">
        <section className="card">
          <h2>Ranking</h2>
          <div className="tableWrap">
            <table>
              <thead>
                <tr>
                  <th>#</th>
                  <th>Ticker</th>
                  <th>Fecha</th>
                  <th>Close</th>
                  <th>Estado</th>
                  <th>Final</th>
                  <th>Tech</th>
                  <th>Fund</th>
                </tr>
              </thead>
              <tbody>
                {filteredRows.map((r, i) => (
                  <tr
                    key={r.ticker + i}
                    className={selected === r.ticker ? "active" : ""}
                    onClick={() => openDetail(r.ticker)}
                    style={{ cursor: "pointer" }}
                    title={r.resumen}
                  >
                    <td>{i + 1}</td>
                    <td>{r.ticker}</td>
                    <td>{r.date}</td>
                    <td>{fmt(r.close)}</td>
                    <td>
                      <span className={`badge badge-${String(r.estado ?? "N/A").toUpperCase()}`}>
                        {r.estado ?? "N/A"}
                      </span>
                    </td>
                    <td>{fmt0(r.finalScore)}</td>
                    <td>{fmt0(r.techScore)}</td>
                    <td>{fmt0(r.fundScore)}</td>
                  </tr>
                ))}
                {!filteredRows.length && (
                  <tr>
                    <td colSpan="8">
                      {rows.length
                        ? "No hay resultados con esos filtros (probá 'Todos' o borrá el buscador)."
                        : "Sin datos (tocá Scan TOP 10)."}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          <small className="muted">
            Tip: poné el mouse sobre una fila para ver el resumen.
          </small>
        </section>

        <section className="card">
          <h2>Detalle</h2>

          {!selected && <div className="muted">Elegí un ticker del ranking.</div>}
          {loadingDetail && <div className="muted">Cargando detalle...</div>}

          {detail && !loadingDetail && (
            <>
              <div className="kpis">
                <KPI label="Ticker" value={detail.ticker ?? selected} />
                <KPI label="Fecha" value={detail.date} />
                <KPI label="Close" value={fmt(detail.close)} />
                <KPI label="SMA20" value={fmt(detail.sma20)} />
                <KPI label="SMA50" value={fmt(detail.sma50)} />
                <KPI label="RSI14" value={fmt(detail.rsi14)} />
                <KPI label="ATR14" value={fmt(detail.atr14)} />
                <KPI
                  label="Estado"
                  value={<span className={`badge badge-${String(detail.estado ?? "N/A").toUpperCase()}`}>{detail.estado ?? "N/A"}</span>}
                />
                <KPI label="FinalScore" value={fmt0(detail.finalScore)} />
              </div>

              <h3>Precio ({rangeDays} días)</h3>

              {loadingHistory && <div className="muted">Cargando gráfico...</div>}

              {!loadingHistory && history.length > 0 && (
                <>
                  <div className="chartBox">
                    <ResponsiveContainer width="100%" height="100%">
                      <LineChart data={history}>
                        <XAxis
                          dataKey="date"
                          tickFormatter={(label) => {
                            // label viene "YYYY-MM-DD"
                            const d = new Date(label);
                            if (Number.isNaN(d.getTime())) return label;
                            const dd = String(d.getDate()).padStart(2, "0");
                            const mm = String(d.getMonth() + 1).padStart(2, "0");
                            return `${dd}/${mm}`;
                          }}
                          minTickGap={28}
                        />
                        <YAxis hide domain={["auto", "auto"]} />
                        <Tooltip content={<ProTooltip />} cursor={{ strokeDasharray: "3 3" }} />
                          formatter={(value, name) => {
                            const n = Number(value);
                            const labelMap = { close: "Close", sma20: "SMA20", sma50: "SMA50" };
                            return [`${Number.isFinite(n) ? n.toFixed(2) : "-"}`, labelMap[name] ?? name];
                          }}
                        />

                        <Line type="monotone" dataKey="close" dot={false} strokeWidth={2} stroke="#60a5fa" />
                        <Line type="monotone" dataKey="sma20" dot={false} strokeWidth={2} stroke="#f59e0b" />
                        <Line type="monotone" dataKey="sma50" dot={false} strokeWidth={2} stroke="#a78bfa" />
                      </LineChart>
                    </ResponsiveContainer>
                  </div>

                  <div className="legend">
                    <span className="dot dotClose" /> Close
                    <span className="dot dotSma20" /> SMA20
                    <span className="dot dotSma50" /> SMA50
                  </div>
                </>
              )}

              <h3>Resumen</h3>
              <pre className="pre">{detail.resumen ?? ""}</pre>

              <h3>Reporte técnico</h3>
              <pre className="pre">{detail.reporte ?? ""}</pre>

              <h3>Fundamental</h3>
              <pre className="pre">{detail.fundamentalReport ?? ""}</pre>
            </>
          )}
        </section>
      </main>
    </div>
  );
}

function KPI({ label, value }) {
  return (
    <div className="kpi">
      <div className="kpiLabel">{label}</div>
      <div className="kpiValue">{value ?? "-"}</div>
    </div>
  );
}

function fmt(n) {
  if (n === null || n === undefined) return "-";
  if (typeof n === "number") return Number.isFinite(n) ? n.toFixed(2) : "-";
  return String(n);
}
function fmt0(n) {
  if (n === null || n === undefined) return "-";
  if (typeof n === "number") return Number.isFinite(n) ? Math.round(n) : "-";
  return String(n);
}

function addSMA(points, period, key) {
  return points.map((p, i) => {
    if (i + 1 < period) return { ...p, [key]: null };
    let sum = 0;
    for (let j = i - period + 1; j <= i; j++) sum += Number(points[j].close);
    return { ...p, [key]: sum / period };
  });
}

function ProTooltip({ active, payload, label }) {
  if (!active || !payload?.length) return null;

  // label viene como "YYYY-MM-DD"
  const d = new Date(label);
  const dateText = Number.isNaN(d.getTime())
    ? label
    : `${String(d.getDate()).padStart(2, "0")}/${String(d.getMonth() + 1).padStart(2, "0")}/${d.getFullYear()}`;

  const byKey = {};
  for (const p of payload) byKey[p.dataKey] = p.value;

  const close = byKey.close;
  const sma20 = byKey.sma20;
  const sma50 = byKey.sma50;

  return (
    <div className="tt">
      <div className="ttDate">{dateText}</div>

      <div className="ttRow">
        <span className="ttKey">Close</span>
        <span className="ttVal">{Number.isFinite(close) ? close.toFixed(2) : "-"}</span>
      </div>

      <div className="ttRow">
        <span className="ttKey">SMA20</span>
        <span className="ttVal">{Number.isFinite(sma20) ? sma20.toFixed(2) : "-"}</span>
      </div>

      <div className="ttRow">
        <span className="ttKey">SMA50</span>
        <span className="ttVal">{Number.isFinite(sma50) ? sma50.toFixed(2) : "-"}</span>
      </div>
    </div>
  );
}