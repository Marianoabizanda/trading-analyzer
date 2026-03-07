import { useEffect, useState } from "react";
import { analyzeTicker, scanTickers, historyTicker } from "./api";
import { LineChart, Line, ResponsiveContainer, Tooltip, YAxis, XAxis } from "recharts";
import "./App.css";

export default function App() {
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

  const [analyzeInput, setAnalyzeInput] = useState("msft.us");

  const [positions, setPositions] = useState(() => {
    const raw = localStorage.getItem("positions");
    return raw ? JSON.parse(raw) : [];
  });

  const [posTicker, setPosTicker] = useState("");
  const [posQty, setPosQty] = useState("");
  const [posDate, setPosDate] = useState("");

  const [plMode, setPlMode] = useState("BUY");
    // BUY | 1D | 5D | 30D

  const [refCloses, setRefCloses] = useState({});
  // key: `${ticker}|${mode}` -> number

  const [tab, setTab] = useState("SCREENER"); // SCREENER | POSITIONS

  const [maView, setMaView] = useState("SMA");
  // "SMA" | "EMA" | "BOTH"

  useEffect(() => {
    if (selected) openDetail(selected);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [rangeDays]);

  useEffect(() => {
    localStorage.setItem("positions", JSON.stringify(positions));
  }, [positions]);

  useEffect(() => {
    if (plMode === "BUY") return;

    const uniq = Array.from(new Set(positions.map((p) => p.ticker)));
    uniq.forEach((t) => {
      ensureRefClose(t);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [plMode, positions]);

  useEffect(() => {
    if (plMode !== "BUY") return;

    positions.forEach((p) => {
      if (!Number.isFinite(p.buyPrice)) ensureBuyPrice(p);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [plMode, positions]);

  async function runScan() {
    try {
      setError("");
      setLoadingScan(true);

      const fallback =
        "msft.us,aapl.us,amzn.us,goog.us,meta.us,nvda.us,tsla.us,spy.us,qqq.us,ko.us";

      const data = await scanTickers(fallback);

      setRows(data);
      setSearch("");
      setSelected(null);
      setDetail(null);
      setHistory([]);
    } catch (e) {
      setError(e?.message ?? "Error");
    } finally {
      setLoadingScan(false);
    }
  }

  async function viewTicker(ticker) {
    setTab("SCREENER");
    await openDetail(ticker);
    // opcional: scroll suave al detalle
    setTimeout(() => {
      document.getElementById("detail-card")?.scrollIntoView({ behavior: "smooth", block: "start" });
    }, 50);
  }

  async function runAnalyze() {
    const t = String(analyzeInput ?? "").trim();
    if (!t) return;
    await openDetail(t);
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

      const buffer = 120;
      const raw = await historyTicker(ticker, rangeDays + buffer);

      const withSma20 = addSMA(raw, 20, "sma20");
      const withSma50 = addSMA(withSma20, 50, "sma50");

      const withEma20 = addEMA(withSma50, 20, "ema20");
      const withEma50 = addEMA(withEma20, 50, "ema50");


      const sliced = withEma50.slice(Math.max(0, withEma50.length - rangeDays));
      console.log(withEma50.slice(-5));
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
    const matchEstado =
      estadoFilter === "ALL" || String(r.estado ?? "").toUpperCase() === estadoFilter;

    return matchSearch && matchEstado;
  });

  useEffect(() => {
    runScan();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function addPosition() {
    const t = String(posTicker || "").trim().toLowerCase();
    const qty = Number(posQty);
    const date = String(posDate || "").trim(); // YYYY-MM-DD

    if (!t) return setError("Ingresá un ticker (ej: msft.us)");
    if (!Number.isFinite(qty) || qty <= 0) return setError("Cantidad inválida");
    if (!date) return setError("Elegí una fecha de compra");

    setError("");

    const item = {
      id: crypto.randomUUID(),
      ticker: t,
      qty,
      buyDate: date,     // <-- guardamos fecha
      buyPrice: NaN,     // <-- se calcula desde history
    };

    setPositions((prev) => [item, ...prev]);

    setPosTicker("");
    setPosQty("");
    setPosDate("");
  }

  function removePosition(id) {
    setPositions((prev) => prev.filter((p) => p.id !== id));
  }

  function getCloseForTicker(tickerLower) {
    // si el detalle abierto coincide, usamos ese close
    if (detail && selected && String(selected).toLowerCase() === tickerLower) return Number(detail.close);

    // si está en el ranking, usamos close del scan
    const r = rows.find((x) => String(x.ticker ?? "").toLowerCase() === tickerLower);
    if (r) return Number(r.close);

    return NaN;
  }

  function findBuyCloseFromHistory(history, buyDate) {
    // history: [{date:"YYYY-MM-DD", close:...}, ...] ordenado viejo->nuevo
    // si no existe exacta, buscamos hacia atrás (último día hábil anterior)
    for (let i = history.length - 1; i >= 0; i--) {
      if (history[i].date <= buyDate) {
        return Number(history[i].close);
      }
    }
    return NaN;
  }

  async function ensureBuyPrice(position) {
    if (Number.isFinite(position.buyPrice)) return;

    // traemos suficiente historia para cubrir la fecha elegida
    // 400 días suele alcanzar para la mayoría
    const h = await historyTicker(position.ticker, 400);
    h.sort((a, b) => new Date(a.date) - new Date(b.date));
    const buyClose = findBuyCloseFromHistory(h, position.buyDate);

    setPositions((prev) =>
      prev.map((p) =>
        p.id === position.id ? { ...p, buyPrice: buyClose } : p
      )
    );
  }

  async function getRefClose(tickerLower, mode) {
    if (mode === "BUY") return NaN; // se usa buyPrice, no history

    const days = mode === "1D" ? 3 : mode === "5D" ? 8 : 40;
    // buffers por fines de semana/feriados

    const h = await historyTicker(tickerLower, days);
    if (!h || h.length < 2) return NaN;

    // tomamos el close de "hace N ruedas" aproximado
    if (mode === "1D") return Number(h[h.length - 2].close);
    if (mode === "5D") return Number(h[Math.max(0, h.length - 6)].close);
    if (mode === "30D") return Number(h[Math.max(0, h.length - 31)].close);

    return NaN;
  }

  async function ensureRefClose(tickerLower) {
    if (plMode === "BUY") return;

    const key = `${tickerLower}|${plMode}`;
    if (refCloses[key] !== undefined) return;

    const ref = await getRefClose(tickerLower, plMode);
    setRefCloses((prev) => ({ ...prev, [key]: ref }));
  }

  async function ensureRefClose(tickerLower) {
      if (plMode === "BUY") return;

      const key = `${tickerLower}|${plMode}`;
      if (refCloses[key] !== undefined) return;

      const ref = await getRefClose(tickerLower, plMode);
      setRefCloses((prev) => ({ ...prev, [key]: ref }));
    }

    const emaCross = detectCross(history, "ema20", "ema50", "EMA20", "EMA50");
    const smaCross = detectCross(history, "sma20", "sma50", "SMA20", "SMA50");


      return (
        <div className="page">
          <header className="header">
            <div>
              <h1>Trading Analyzer</h1>
              <p>Ranking + Detalle </p>
            </div>
          </header>

              <div className="tabs">
            <button
              className={`tabBtn ${tab === "SCREENER" ? "active" : ""}`}
              onClick={() => setTab("SCREENER")}
              type="button"
            >
              Screener
            </button>

            <button
              className={`tabBtn ${tab === "POSITIONS" ? "active" : ""}`}
              onClick={() => setTab("POSITIONS")}
              type="button"
            >
              Posiciones
            </button>
          </div>

          {/* SCREENER */}
          {tab === "SCREENER" && (
          <>
          <section className="controls">
            <div className="controlsRow">
              <label className="field fieldWide">
                <span>Analizar ticker</span>
                <div className="analyzeRow">
                  <input
                    value={analyzeInput}
                    onChange={(e) => setAnalyzeInput(e.target.value)}
                    placeholder="ej: msft.us"
                  />
                  <button
                    className="ghostBtn"
                    type="button"
                    onClick={runAnalyze}
                    disabled={loadingDetail || loadingHistory}
                  >
                    Analizar
                  </button>
                </div>
              </label>

              <label className="field">
                <span>Filtrar ranking</span>
                <input
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  placeholder="ej: aapl"
                />
              </label>


              <label className="field fieldSmall">
                <span>Estado</span>
                <select value={estadoFilter} onChange={(e) => setEstadoFilter(e.target.value)}>
                  <option value="ALL">Todos</option>
                  <option value="ENTRAR">ENTRAR</option>
                  <option value="VIGILAR">VIGILAR</option>
                  <option value="SALIR">SALIR</option>
                  <option value="EVITAR">EVITAR</option>
                  <option value="MANTENER">MANTENER</option>
                </select>
              </label>

              <button className="primaryBtn" onClick={runScan} disabled={loadingScan}>
                {loadingScan ? "Escaneando..." : "Scan TOP 10"}
              </button>
            </div>
          </section>



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
            <small className="muted">Tip: pasá el mouse sobre una fila para ver el resumen.</small>
          </section>

          <section className="card" id="detail-card">
            <h2>Detalle</h2>

            {!selected && <div className="muted">Elegí un ticker del ranking o analizá uno.</div>}
            {loadingDetail && <div className="muted">Cargando detalle...</div>}

            {detail && !loadingDetail && (
                <>
                  {(() => {
                    const opportunity = getOpportunity(detail);
                    const setupLabel =
                      detail.setupType === "BREAKOUT" ? "Breakout" :
                      detail.setupType === "PULLBACK" ? "Pullback" :
                      "Esperar (no comprar)";

                return (
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
                    value={
                      <span className={`badge badge-${String(detail.estado ?? "N/A").toUpperCase()}`}>
                        {detail.estado ?? "N/A"}
                      </span>
                    }
                  />
                  <KPI label="FinalScore" value={fmt0(detail.finalScore)} />
                </div>

                {/* SETUP (Señal intuitiva) */}
                <div className="opportunityBox">
                  <h3>🧭 Señal (Setup)</h3>

                  <div className="kpis" style={{ marginTop: 10 }}>
                    <KPI label="Tipo" value={setupLabel} />
                    <KPI label="SetupScore" value={detail.setupScore != null ? `${detail.setupScore}/100` : "N/A"} />
                    <KPI
                      label="Breakout"
                      value={
                        <span className={`badge ${detail.isBreakout ? "badge-ENTRAR" : "badge-EVITAR"}`}>
                          {detail.isBreakout ? "Sí" : "No"}
                        </span>
                      }
                    />
                    <KPI label="Nivel (High20)" value={detail.breakoutLevel != null ? fmt(detail.breakoutLevel) : "N/A"} />
                    <KPI label="% vs nivel" value={detail.breakoutPct != null ? `${detail.breakoutPct.toFixed(2)}%` : "N/A"} />
                  </div>

                  <ul style={{ marginTop: 10 }}>
                    {(detail.setupReasons ?? []).map((r, i) => (
                      <li key={i}>{r}</li>
                    ))}
                  </ul>
                </div>


                {opportunity && (
                  <div className="opportunityBox">
                    <h3>🔥 Oportunidad detectada</h3>

                    <ul>
                      {opportunity.reasons.map((r, i) => (
                        <li key={i}>{r}</li>
                      ))}
                    </ul>
                  </div>
                )}
                </>
                );
               })()}

                <h3>Precio ({rangeDays} días)</h3>

                <label className="field fieldSmall">
                    <span>Historial</span>
                    <select value={rangeDays} onChange={(e) => setRangeDays(Number(e.target.value))}>
                      <option value={15}>15 días</option>
                      <option value={30}>30 días</option>
                      <option value={60}>60 días</option>
                    </select>
                  </label>

                  <label className="field fieldSmall">
                    <span>Medias</span>
                    <select value={maView} onChange={(e) => setMaView(e.target.value)}>
                      <option value="SMA">Solo SMA</option>
                      <option value="EMA">Solo EMA</option>
                      <option value="BOTH">SMA + EMA</option>
                    </select>
                  </label>
                {loadingHistory && <div className="muted">Cargando gráfico...</div>}

                {!loadingHistory && history.length > 0 && (
                  <>
                    <div className="chartBox">
                      <ResponsiveContainer width="100%" height="100%">
                        <LineChart data={history}>
                          <XAxis
                            dataKey="date"
                            tickFormatter={(label) => {
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
                          <Line
                            type="monotone"
                            dataKey="close"
                            dot={false}
                            strokeWidth={2}
                            stroke="#60a5fa"
                          />

                          {(maView === "SMA" || maView === "BOTH") && (
                            <>
                              <Line type="monotone" dataKey="sma20" dot={false} strokeWidth={2} stroke="#f59e0b" />
                              <Line type="monotone" dataKey="sma50" dot={false} strokeWidth={2} stroke="#a78bfa" />
                            </>
                          )}

                          {(maView === "EMA" || maView === "BOTH") && (
                            <>
                              <Line type="monotone" dataKey="ema20" dot={false} strokeWidth={2} stroke="#22c55e" />
                              <Line type="monotone" dataKey="ema50" dot={false} strokeWidth={2} stroke="#ef4444" />
                            </>
                          )}
                        </LineChart>
                      </ResponsiveContainer>
                    </div>

                    <div className="legend">
                      <span className="dot dotClose" /> Close

                      {(maView === "SMA" || maView === "BOTH") && (
                        <>
                          <span className="dot dotSma20" /> SMA20
                          <span className="dot dotSma50" /> SMA50
                        </>
                      )}

                      {(maView === "EMA" || maView === "BOTH") && (
                        <>
                          <span className="dot dotEma20" /> EMA20
                          <span className="dot dotEma50" /> EMA50
                        </>
                      )}
                    </div>

                    <div className="crossBox">
                      {(maView === "EMA" || maView === "BOTH") && (
                        <div className="crossItem">
                          <strong>Cruce EMA:</strong> {emaCross.text}
                        </div>
                      )}

                      {(maView === "SMA" || maView === "BOTH") && (
                        <div className="crossItem">
                          <strong>Cruce SMA:</strong> {smaCross.text}
                        </div>
                      )}
                    </div>
                  </>
                )}

                <ReportBox title="Resumen" text={detail.resumen} />
                <ReportBox title="Reporte técnico" text={detail.reporte} />
                <ReportBox title="Fundamental" text={detail.fundamentalReport} />
              </>
                )}
          </section>
      </main>
     </>
    )}
        {tab === "POSITIONS" && (
            <>
            {error && <div className="error">⚠ {error}</div>}

          <section className="card" style={{ marginBottom: 14 }}>
            <div className="cardHead">
              <h2 style={{ margin: 0 }}>Mis posiciones</h2>
              <span className="muted">Simulador de compra</span>
            </div>

            <label className="field fieldSmall" style={{ minWidth: 170 }}>
              <span>Comparar</span>
              <select value={plMode} onChange={(e) => { setPlMode(e.target.value); setRefCloses({}); }}>
                <option value="BUY">Desde compra</option>
                <option value="1D">Desde ayer (1D)</option>
                <option value="5D">Desde 5D</option>
                <option value="30D">Desde 30D</option>
              </select>
            </label>

            <div className="posForm">
              <input
                value={posTicker}
                onChange={(e) => setPosTicker(e.target.value)}
                placeholder="Ticker (ej: msft.us)"
              />
              <input
                value={posQty}
                onChange={(e) => setPosQty(e.target.value)}
                placeholder="Cantidad"
                inputMode="numeric"
              />
              <input
                value={posDate}
                onChange={(e) => setPosDate(e.target.value)}
                type="date"
              />
              <button className="ghostBtn" type="button" onClick={addPosition}>
                Agregar
              </button>
            </div>

            <div className="tableWrap" style={{ marginTop: 12 }}>
              <table>
                <thead>
                  <tr>
                    <th>Ticker</th>
                    <th>Qty</th>
                    <th>{plMode === "BUY" ? "Buy" : "Ref"}</th>
                    <th>Close</th>
                    <th>P/L $</th>
                    <th>P/L %</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {positions.map((p) => {

                    const close = getCloseForTicker(p.ticker);

                    // base = contra qué comparo
                    let base = p.buyPrice;

                    if (plMode !== "BUY") {
                      const key = `${p.ticker}|${plMode}`;
                      const ref = refCloses[key];
                      base = Number.isFinite(ref) ? ref : NaN;
                    }

                    const pl = Number.isFinite(close) && Number.isFinite(base) ? (close - base) * p.qty : NaN;
                    const plPct = Number.isFinite(close) && Number.isFinite(base) ? ((close - base) / base) * 100 : NaN;

                    return (
                      <tr key={p.id}>
                        <td>{p.ticker.toUpperCase()}</td>
                        <td>{p.qty}</td>


                        <td>
                          {plMode === "BUY" ? (
                            <div>
                              <div style={{ fontSize: 12, opacity: 0.75 }}>{p.buyDate}</div>
                              <div>{Number.isFinite(p.buyPrice) ? fmt(p.buyPrice) : <span className="muted">…</span>}</div>
                            </div>
                          ) : (
                            Number.isFinite(base) ? fmt(base) : <span className="muted">…</span>
                          )}
                        </td>

                        <td>{Number.isFinite(close) ? fmt(close) : <span className="muted">—</span>}</td>

                        <td className={Number.isFinite(pl) ? (pl >= 0 ? "plPos" : "plNeg") : ""}>
                          {Number.isFinite(pl) ? fmt(pl) : <span className="muted">—</span>}
                        </td>

                        <td className={Number.isFinite(plPct) ? (plPct >= 0 ? "plPos" : "plNeg") : ""}>
                          {Number.isFinite(plPct) ? `${plPct.toFixed(2)}%` : <span className="muted">—</span>}
                        </td>

                        <td style={{ display: "flex", gap: 8 }}>
                          <button className="pillBtn" type="button" onClick={() => viewTicker(p.ticker)}>
                            Ver
                          </button>
                          <button className="pillBtn danger" type="button" onClick={() => removePosition(p.id)}>
                            Borrar
                          </button>
                        </td>
                      </tr>
                    );
                  })}

                  {!positions.length && (
                    <tr>
                      <td colSpan="7" className="muted">
                        No tenés posiciones todavía. Agregá una arriba.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>

            <small className="muted" style={{ display: "inline-block", marginTop: 10 }}>
              Tip: para que aparezca “Close”, primero hacé Scan TOP 10 o Analizar el ticker.
            </small>
          </section>
         </>
        )}


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

    function getOpportunity(detail) {
      if (!detail) return null;

      const rsi = Number(detail.rsi14);
      const sma20 = Number(detail.sma20);
      const sma50 = Number(detail.sma50);
      const score = Number(detail.finalScore);

      const reasons = [];

      if (rsi < 40) reasons.push("RSI bajo (posible rebote)");
      if (sma20 > sma50) reasons.push("SMA20 > SMA50 (tendencia alcista)");
      if (score >= 75) reasons.push("Score alto");

      if (reasons.length >= 2) {
        return {
          level: "OPPORTUNITY",
          reasons
        };
      }

      return null;
    }

    function addSMA(points, period, key) {
      return points.map((p, i) => {
        if (i + 1 < period) return { ...p, [key]: null };
        let sum = 0;
        for (let j = i - period + 1; j <= i; j++) sum += Number(points[j].close);
        return { ...p, [key]: sum / period };
      });
    }

    function addEMA(points, period, key) {
      if (!points?.length) return [];

      const k = 2 / (period + 1);
      let prevEma = null;

      return points.map((p, i) => {
        const close = Number(p.close);

        if (i + 1 < period) {
          return { ...p, [key]: null };
        }

        // primer EMA = SMA inicial
        if (i + 1 === period) {
          let sum = 0;
          for (let j = 0; j < period; j++) {
            sum += Number(points[j].close);
          }
          prevEma = sum / period;
          return { ...p, [key]: prevEma };
        }

        if (prevEma == null || !Number.isFinite(close)) {
          return { ...p, [key]: null };
        }

        prevEma = (close - prevEma) * k + prevEma;
        return { ...p, [key]: prevEma };
      });
    }

    function detectCross(points, fastKey, slowKey, fastLabel, slowLabel) {
      if (!points || points.length < 2) {
        return { type: "NONE", text: "Sin datos suficientes para evaluar cruces." };
      }

      for (let i = points.length - 1; i > 0; i--) {
        const prevFast = Number(points[i - 1][fastKey]);
        const prevSlow = Number(points[i - 1][slowKey]);
        const currFast = Number(points[i][fastKey]);
        const currSlow = Number(points[i][slowKey]);

        if (
          !Number.isFinite(prevFast) ||
          !Number.isFinite(prevSlow) ||
          !Number.isFinite(currFast) ||
          !Number.isFinite(currSlow)
        ) {
          continue;
        }

        const bullish = prevFast <= prevSlow && currFast > currSlow;
        const bearish = prevFast >= prevSlow && currFast < currSlow;

        const ruedas = points.length - 1 - i;

        if (bullish) {
          return {
            type: "BULLISH",
            text: `${fastLabel} cruzó por arriba de ${slowLabel} hace ${ruedas} rueda(s). Esto suele interpretarse como una señal alcista o de mejora en el impulso.`,
            at: points[i]?.date ?? null
          };
        }

        if (bearish) {
          return {
            type: "BEARISH",
            text: `${fastLabel} cruzó por debajo de ${slowLabel} hace ${ruedas} rueda(s). Esto suele interpretarse como una señal bajista o de pérdida de fuerza.`,
            at: points[i]?.date ?? null
          };
        }
      }

      const last = points[points.length - 1];
      const fast = Number(last?.[fastKey]);
      const slow = Number(last?.[slowKey]);

      if (Number.isFinite(fast) && Number.isFinite(slow)) {
        if (fast > slow) {
          return {
            type: "ABOVE",
            text: `No hubo un cruce reciente, pero ${fastLabel} sigue por arriba de ${slowLabel}. Eso sugiere que la señal alcista sigue vigente.`
          };
        }
        if (fast < slow) {
          return {
            type: "BELOW",
            text: `No hubo un cruce reciente, pero ${fastLabel} sigue por debajo de ${slowLabel}. Eso sugiere que la señal sigue débil o bajista.`
          };
        }
      }

      return { type: "NONE", text: "Sin cruce reciente." };
    }

    function getPriceAtDate(history, date) {
      if (!history?.length) return NaN;

      const target = new Date(date).getTime();

      let closest = history[0];
      let diff = Math.abs(new Date(closest.date).getTime() - target);

      for (const p of history) {
        const d = new Date(p.date).getTime();
        const curDiff = Math.abs(d - target);

        if (curDiff < diff) {
          diff = curDiff;
          closest = p;
        }
      }

      return Number(closest.close);
    }

    function ProTooltip({ active, payload, label }) {
      if (!active || !payload?.length) return null;

      const d = new Date(label);
      const dateText = Number.isNaN(d.getTime())
        ? label
        : `${String(d.getDate()).padStart(2, "0")}/${String(d.getMonth() + 1).padStart(
            2,
            "0"
          )}/${d.getFullYear()}`;

      const byKey = {};
      for (const p of payload) byKey[p.dataKey] = p.value;

      const close = byKey.close;
      const sma20 = byKey.sma20;
      const sma50 = byKey.sma50;
      const ema20 = byKey.ema20;
      const ema50 = byKey.ema50;

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

          <div className="ttRow">
            <span className="ttKey">EMA20</span>
            <span className="ttVal">{Number.isFinite(ema20) ? ema20.toFixed(2) : "-"}</span>
          </div>

          <div className="ttRow">
            <span className="ttKey">EMA50</span>
            <span className="ttVal">{Number.isFinite(ema50) ? ema50.toFixed(2) : "-"}</span>
          </div>
        </div>
      );
    }

    function ReportBox({ title, text }) {
      const lines = String(text ?? "")
        .split("\n")
        .map((l) => l.trim())
        .filter(Boolean);

      return (
        <div className="reportBox">
          <div className="reportHead">{title}</div>
          <div className="reportBody">
            {lines.length <= 1 ? (
              <p>{lines[0] ?? ""}</p>
            ) : (
              <ul>
                {lines.map((l, i) => (
                  <li key={i}>{l}</li>
                ))}
              </ul>
            )}
          </div>
        </div>
      );
    }



