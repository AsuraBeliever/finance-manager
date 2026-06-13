# Plan del proyecto — Finanzas

App de finanzas personales para gestionar carteras ilimitadas, transacciones e inversiones, con MXN como moneda principal y soporte multi-moneda. Desde v2.0.0 vive en la nube (Cloudflare Workers + D1) como PWA multiusuario; el escritorio es un shell Tauri.

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
- [x] **M6 — Pulido (`v1.0.0`)** `feat/polish`: validaciones, confirmaciones, formato es-MX, ícono, clippy/fmt, bundle instalable. Nota: AppImage quedó fuera de los targets (linuxdeploy falla en Arch); se generan .deb y .rpm, y el binario directo vive en `src-tauri/target/release/finanzas`.

### v2.0.0 — a la nube (`feat/mobile`)

- [x] **M0 — Workspace**: crate `finanzas-core` (calculadoras con `CalcContext`, parsers de mercado, modelos, errores) compartido por escritorio y worker; tests nativos intactos.
- [x] **M1 — Worker + D1**: crate `worker/` (workers-rs), migraciones D1 multiusuario, dispatcher RPC `POST /api/rpc/<comando>` con los 35 comandos; escrituras multi-statement vía `batch()`.
- [x] **M2 — Auth**: registro con código de invitación (secret), PBKDF2-SHA256 vía SubtleCrypto, sesiones cookie HttpOnly (hash en DB, expiración deslizante), aislamiento por `user_id` verificado.
- [x] **M3 — Mercado**: fetch de Banxico/open.er-api/BONDDIA/CoinGecko vía worker Fetch + cron trigger diario (07:00 UTC).
- [x] **M4 — PWA**: api.ts → fetch RPC, login/registro, manifest + íconos + metas iOS, bottom-nav móvil, service worker (jamás cachea /api/*).
- [x] **M5 — Deploy + migración**: https://finanzas.asura.workers.dev, datos locales migrados a D1 con checksums verificados (conteos y saldos al centavo); `finanzas.db` local conservada como respaldo de solo lectura.
- [x] **M6 — Escritorio shell**: la ventana Tauri carga la URL desplegada; docs actualizadas; merge + tag v2.0.0.

### v2.1.0 — cuenta y offline (`feat/account-offline`)

- [x] **Cuenta**: cambiar contraseña (revoca las demás sesiones) y lista de dispositivos con sesión (User-Agent + última actividad, revocar individual o todas) en Ajustes.
- [x] **Offline consulta**: caché de queries persistida en localStorage; sin red la app abre con los últimos datos y banner de «sin conexión».
- [x] **Offline captura**: outbox append-only para ingresos/gastos/transferencias con idempotencia server-side (`transactions.client_id`); panel «Pendientes de sincronizar» y drenado automático al reconectar.

Cada milestone se desarrolla en su branch `feat/*`, se mergea a `main` con `--no-ff` al verificar, y se etiqueta con su tag semver.

## Verificación final (v1.0.0)

1. `npm run tauri dev`: crear carteras de varias categorías y monedas.
2. Registrar ingresos, gastos y transferencias; validar saldos.
3. Dashboard suma el total en MXN correctamente al cambiar tipos de cambio.
4. Crear inversiones Nu cajita y CETES; validar contra cálculo a mano.
5. `cargo test` verde; `npm run tauri build` genera AppImage/.deb funcional.
