# Decisiones de arquitectura (ADR-lite)

Una entrada por decisión. No se borran: si una decisión se revierte, se agrega una entrada nueva que la supersede.

## 2026-06-10 — rusqlite en Rust en lugar de tauri-plugin-sql

La lógica de negocio (agregación de saldos, atomicidad de transferencias, matemáticas de inversiones) vive en un solo lugar tipado. Con `tauri-plugin-sql` el frontend escribiría SQL crudo sin tipos, las transacciones multi-statement serían incómodas y las fórmulas se duplicarían en TS. Con rusqlite: modelos serde tipados, `BEGIN/COMMIT` reales, aritmética exacta `i64`, y fórmulas testeables con `cargo test`. El feature `bundled` compila SQLite dentro del binario y evita dependencias del sistema en Arch.

## 2026-06-10 — Motor de inversiones en Rust, no en TS

Misma razón: única fuente de verdad junto a los datos, control exacto de redondeo, testeable con cargo. El frontend nunca calcula dinero; recibe centavos y series listas.

## 2026-06-10 — Dinero en centavos, tipos de cambio en micros, tasas en basis points

Evita errores de punto flotante en almacenamiento y aritmética. Los flotantes solo aparecen transitoriamente dentro de las fórmulas de interés (inevitable por las potencias) y el resultado se redondea a centavos una sola vez.

## 2026-06-10 — Saldo calculado, nunca almacenado

`saldo = initial_balance_cents + Σ(transacciones con signo)`. Elimina toda posibilidad de desincronización entre saldo y historial; SQLite agrega miles de filas sin problema a esta escala.

## 2026-06-10 — Transferencias como 2 filas ligadas por transfer_group_id

Permite cross-currency naturalmente (cada pierna en la moneda de su cartera) y simplifica las consultas por cartera. La atomicidad la garantiza la transacción SQL al insertar/borrar.

## 2026-06-10 — Fechas de negocio date-only (YYYY-MM-DD)

Para finanzas personales la hora no aporta; simplifica agrupaciones por día/mes y evita problemas de zona horaria.

## 2026-06-10 — createHashRouter en lugar de history routing

La app de producción se sirve desde el protocolo custom de Tauri, donde no hay servidor que reescriba rutas. Hash routing funciona idéntico en dev y producción sin configuración extra.

## 2026-06-10 — TanStack Query sin estado global adicional

Todo el estado relevante es estado de servidor (la DB). Invalidación de queries tras cada mutación reemplaza cualquier store manual. Se reevaluará si aparece estado puramente de UI compartido.
