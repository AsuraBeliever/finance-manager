# Finanzas

App de escritorio de finanzas personales (Tauri 2 + React 19 + SQLite). Carteras ilimitadas, transacciones, panel de patrimonio multi-moneda e inversiones con calculadoras de Nu cajita, CETES, tasa fija y valor manual.

## Características

- **Carteras**: efectivo, tarjetas, cuentas de ahorro… con nombre, categoría, moneda, saldo inicial y color. El saldo siempre se calcula del historial.
- **Transacciones**: ingresos, gastos y transferencias entre carteras (incluso entre monedas distintas), con categorías y filtros.
- **Resumen**: patrimonio total en MXN sumando carteras e inversiones, desglose por moneda, gráficas de distribución y de flujo mensual.
- **Inversiones**: calculadoras conectables — Nu cajita (interés compuesto diario), CETES (ACT/360 con ISR opcional), tasa fija personalizada y valor manual con snapshots; proyecciones a futuro.
- **Multi-moneda**: MXN como base, tipos de cambio manuales en Ajustes.

## Desarrollo

```sh
npm install
npm run tauri dev     # correr en desarrollo
npm run tauri build   # generar .deb / .rpm / AppImage
cargo test --manifest-path src-tauri/Cargo.toml   # tests de fórmulas y datos
```

Requisitos: Rust, Node 20+, y en Linux `webkit2gtk-4.1`.

## Documentación

La arquitectura, el modelo de datos, las fórmulas de inversión y el roadmap viven en [`docs/`](docs/PLAN.md). Las convenciones del proyecto están en [`CLAUDE.md`](CLAUDE.md).

Los datos se guardan localmente en `~/.local/share/com.asura.finanzas/finanzas.db`. Nada sale de tu máquina.
