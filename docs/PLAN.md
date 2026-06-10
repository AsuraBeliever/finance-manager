# Plan del proyecto — Finanzas

App de finanzas personales de escritorio (Tauri 2 + React) para gestionar carteras ilimitadas, transacciones e inversiones, con MXN como moneda principal y soporte multi-moneda.

## Objetivos

- **Carteras ilimitadas**: efectivo, tarjetas de débito/crédito, cuentas de ahorro, etc. Cada una con nombre, categoría, moneda, saldo inicial, color y notas.
- **Panel de resumen**: patrimonio total en MXN sumando todas las carteras (con conversión de divisas), desglose por moneda y por cartera, ingresos/gastos recientes.
- **Transacciones**: ingresos, gastos y transferencias entre carteras. El saldo de cada cartera se calcula siempre como saldo inicial + historial (nunca se almacena).
- **Inversiones** con calculadoras conectables:
  - `nu_cajita` — Cajitas de Nu (interés compuesto diario, tasa anual editable).
  - `cetes` — CETES vía cetesdirecto (descuento ACT/360, plazos 28/91/182/364, ISR opcional).
  - `fixed_rate` — tasa fija personalizada (compuesto diario/mensual o simple).
  - `manual` — el usuario registra el valor actual con snapshots.
- **Multi-moneda**: tipos de cambio manuales (API de Banxico en el roadmap).

## Documentos

| Documento | Contenido |
|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | Stack, capas, catálogo de comandos Tauri, estructura de carpetas |
| [DATA_MODEL.md](DATA_MODEL.md) | Esquema SQL canónico, convenciones de datos, semántica de saldos y transferencias |
| [INVESTMENTS.md](INVESTMENTS.md) | Fórmulas con derivación y guía para agregar calculadoras |
| [ROADMAP.md](ROADMAP.md) | Funcionalidad post-v1 |
| [DECISIONS.md](DECISIONS.md) | Registro de decisiones de arquitectura (ADR-lite) |

## Milestones

- [x] **M0 — Scaffold (`v0.1.0`)**: Tauri 2 + React 19 + TS + Vite 7, Tailwind 4, React Router 7 (hash), TanStack Query 5, layout con sidebar en español, docs/, CLAUDE.md, git.
- [x] **M1 — Capa de datos (`v0.2.0`)** `feat/db`: rusqlite (bundled), migraciones embebidas, seeds (monedas, categorías), modelos, `AppError`, comandos de lectura básicos.
- [x] **M2 — Carteras (`v0.3.0`)** `feat/wallets`: CRUD completo + página Carteras con formulario modal y archivado.
- [x] **M3 — Transacciones (`v0.4.0`)** `feat/transactions`: ingreso/gasto/transferencia (2 piernas atómicas), detalle de cartera con historial, filtros, borrado en par.
- [x] **M4 — Dashboard (`v0.5.0`)** `feat/dashboard`: resumen con conversión a MXN, edición de tipos de cambio, gráficas (dona por cartera, barras 6 meses).
- [x] **M5 — Inversiones (`v0.6.0`)** `feat/investments`: trait + 4 calculadoras con tests unitarios, páginas con proyección, integración al dashboard.
- [ ] **M6 — Pulido (`v1.0.0`)** `feat/polish`: validaciones, confirmaciones, formato es-MX, ícono, clippy/fmt, bundle instalable.

Cada milestone se desarrolla en su branch `feat/*`, se mergea a `main` con `--no-ff` al verificar, y se etiqueta con su tag semver.

## Verificación final (v1.0.0)

1. `npm run tauri dev`: crear carteras de varias categorías y monedas.
2. Registrar ingresos, gastos y transferencias; validar saldos.
3. Dashboard suma el total en MXN correctamente al cambiar tipos de cambio.
4. Crear inversiones Nu cajita y CETES; validar contra cálculo a mano.
5. `cargo test` verde; `npm run tauri build` genera AppImage/.deb funcional.
