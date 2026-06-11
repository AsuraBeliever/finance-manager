# Finanzas — app de finanzas personales (Tauri 2 + React)

App de escritorio para gestionar carteras, transacciones e inversiones. MXN principal, multi-moneda. UI en español (es-MX), código y comentarios en inglés.

## Comandos

```sh
npm run tauri dev                                  # correr la app
npm run tauri build                                # bundle (.deb / AppImage)
npm run build                                      # tsc + vite build (chequeo rápido de frontend)
cargo check  --manifest-path src-tauri/Cargo.toml  # chequeo rápido de Rust
cargo test   --manifest-path src-tauri/Cargo.toml  # tests (fórmulas de inversión)
cargo clippy --manifest-path src-tauri/Cargo.toml
cargo fmt    --manifest-path src-tauri/Cargo.toml
```

## Arquitectura (resumen — detalle en docs/ARCHITECTURE.md)

- **Toda la lógica de dinero vive en Rust** (`src-tauri/src/commands/`, `src-tauri/src/investments/`). El frontend NUNCA calcula dinero: solo renderiza centavos/series que regresa el backend vía wrappers tipados en `src/lib/api.ts` + TanStack Query.
- SQLite vía rusqlite (`bundled`), `Mutex<Connection>` en managed state, DB en `~/.local/share/com.asura.finanzas/finanzas.db`.
- Saldo de cartera = calculado (initial + Σ transacciones), nunca almacenado.
- Transferencia = 2 filas (`transfer_out`/`transfer_in`) con mismo `transfer_group_id`, insertadas/borradas atómicamente.
- Router: `createHashRouter` (protocolo custom de Tauri no soporta history routing).
- Tipos de cambio: auto-actualización al arrancar vía open.er-api.com (reqwest, silencioso si falla) + botón en Ajustes + override manual. NUNCA sostener el lock de la DB a través de un `await`.

## Convenciones

- **Dinero**: centavos enteros `i64`. **Tipos de cambio**: micros. **Tasas**: basis points. **Fechas de negocio**: TEXT `YYYY-MM-DD`.
- serde `rename_all = "camelCase"` en todo lo que cruza el puente Tauri.
- Strings de UI SOLO en `src/i18n/es.ts` (español); nada hardcodeado en componentes.
- **Cambios de esquema**: solo vía migración nueva en `src-tauri/src/db/mod.rs` + actualizar `docs/DATA_MODEL.md`.
- **Calculadoras de inversión nuevas**: implementar el trait `InvestmentCalculator` + registry + tests + form; guía en `docs/INVESTMENTS.md`.
- Git: conventional commits (`feat:`, `fix:`, `docs:`, `chore:`...); milestone por branch `feat/<nombre>`, merge `--no-ff` a `main`, tag semver al completar. Fixes triviales directo a `main`.
- Tests de fórmulas financieras son obligatorios, con valores de referencia calculados a mano.

## Estado actual

- **v1.3.0**: borrar carteras, DateInput/ConfirmDialog propios (widgets nativos de WebKitGTK rotos/feos), tasas CETES/objetivo desde Banxico SIE (token en Ajustes). Antes: v1.2.0 movimientos de inversión, v1.1.0 tipos de cambio automáticos, v1.0.0 base (M0–M6 en `docs/PLAN.md`).
- Targets de bundle: solo `deb` y `rpm` — AppImage se quitó porque `linuxdeploy` falla en Arch. El binario de release queda en `src-tauri/target/release/finanzas`.
- Trabajo futuro: ver `docs/ROADMAP.md` (presupuestos, recurrentes, export/backup, tasas históricas de cajitas).

## Docs

`docs/PLAN.md` (plan + checklist) · `docs/ARCHITECTURE.md` (capas, catálogo de comandos) · `docs/DATA_MODEL.md` (SQL canónico) · `docs/INVESTMENTS.md` (fórmulas: Nu cajita ACT/365 compuesto diario, CETES ACT/360 + ISR) · `docs/ROADMAP.md` · `docs/DECISIONS.md` (ADRs).
