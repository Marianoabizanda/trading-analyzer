# 📈 Trading Analyzer Engine

Motor de análisis de mercado desarrollado en Java con arquitectura modular.
Combina análisis técnico, análisis fundamental y gestión de riesgo para generar decisiones de trading explicadas y puntuadas.

---

## 🚀 Características principales

- 📊 Indicadores técnicos:
    - SMA (Simple Moving Average)
    - RSI (Relative Strength Index)
    - ATR (Average True Range)

- 🧠 Motor de decisión (DecisionEngine)
    - Genera un score técnico (0–100)
    - Combina score fundamental
    - Produce una acción sugerida (BUY / SELL / HOLD)
    - Incluye explicación textual

- 💰 Gestión de riesgo (RiskManager)
    - Stop Loss dinámico basado en ATR
    - Tamaño de posición basado en % de riesgo y capital

- 📡 Cliente de datos
    - StooqClient para descarga de datos históricos en CSV

- 🧪 Backtesting
    - Backtester y BacktesterPro para simular estrategias

- 🌐 API REST (Spring Boot)
    - TradingController expone endpoints para análisis

---

## 🏗 Arquitectura

El proyecto está dividido en capas:

api → Controladores REST y DTOs
app → Lógica de negocio y motor de decisión
data → Clientes externos (Stooq)
indicators → Cálculo de indicadores técnicos
model → Modelos de dominio (Candle)


---

## 🛠 Tecnologías

- Java 17+
- Spring Boot
- Maven
- Arquitectura modular por capas

---

## ▶ Cómo ejecutar

```bash
mvn clean install
mvn spring-boot:run

O ejecutar TradingApplication desde el IDE.

📌 Objetivo del proyecto

Escalar el motor para:

- Detectar oportunidades en horizontes de días a meses

- Incorporar múltiples estrategias

- Mejorar el scoring con modelos cuantitativos

- Integrar fuentes reales de datos fundamentales

👨‍💻 Autor

Desarrollado por Mariano Abizanda
Proyecto personal enfocado en backend financiero y sistemas de análisis cuantitativo.