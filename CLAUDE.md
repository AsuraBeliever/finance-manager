# Finanzas — finanzas personales en la nube (Cloudflare Workers + D1 + PWA)

App de carteras, transacciones e inversiones. MXN principal, multi-moneda,
multiusuario (registro con código de invitación). El frontend es una PWA
(iPhone vía Safari → «Agregar a pantalla de inicio»); el escritorio es un
shell Tauri que carga la URL desplegada. UI en español (es-MX), código y
comentarios en inglés.

## Comandos

```sh
npm run build                                # tsc + vite build → dist/ (el worker lo sirve)
cargo test --workspace                       # tests (fórmulas + parsers, nativos en finanzas-core)
cargo check -p finanzas-worker --target wasm32-unknown-unknown   # chequeo rápido del worker
cargo clippy --workspace && cargo fmt --all

# desarrollo local (app completa en http://localhost:8787)
cd worker && npx wrangler d1 migrations apply finanzas --local
cd worker && npx wrangler dev                # requiere npm run build previo; INVITE_CODE en worker/.dev.vars
npm run dev                                  # (opcional) Vite con HMR; /api se proxea a :8787

# producción
cd worker && npx wrangler deploy             # publica worker + dist/
cd worker && npx wrangler tail               # logs (cron, errores)
npm run tauri dev                            # shell de escritorio
```

## Arquitectura (resumen — detalle en docs/ARCHITECTURE.md)

- **Toda la lógica de dinero vive en Rust**: pura en `crates/finanzas-core`
  (calculadoras con `CalcContext`, parsers de mercado; tests nativos) y
  orquestación/SQL en `worker/src/handlers/`. El frontend NUNCA calcula
  dinero: solo renderiza lo que regresa `src/lib/api.ts` (RPC fetch) +
  TanStack Query.
- API RPC-style: `POST /api/rpc/<comando>` con cuerpos camelCase; auth en
  `/api/auth/*` (sesiones por cookie HttpOnly; PBKDF2 vía SubtleCrypto nativo,
  NUNCA un crate de hashing puro — límite de 10ms CPU del free tier).
- **D1 no tiene BEGIN/COMMIT**: toda escritura multi-statement va en
  `db.batch()` (transferencias = 2 filas con mismo `transfer_group_id`).
- Saldo de cartera = calculado (initial + Σ transacciones), nunca almacenado.
- Scoping multiusuario: `user_id` en wallets/investments/transaction_categories
  (NULL = seed); transactions/snapshots/movements por JOIN al padre; settings
  PK `(user_id, key)` con usuario 0 = cachés globales.
- Datos de mercado por cron trigger diario (Banxico, open.er-api, BONDDIA,
  CoinGecko) — ver `worker/src/market.rs`.
- Router: `createHashRouter` (funciona igual en web y Tauri).
- **Escritorio = web**: el shell Tauri carga la URL desplegada; `wrangler deploy`
  actualiza web + iPhone + escritorio a la vez (no se recompila el binario salvo
  cambios nativos en `src-tauri/`). Aviso de versión nueva in-app vía
  `registerType: "prompt"` + `src/features/update/UpdateBanner.tsx`.
- El service worker de la PWA JAMÁS cachea `/api/*`.
- No multiplicar centavos×micros en SQL (números D1 → JS f64): esa aritmética
  va en Rust con i128 intermedio.

## Convenciones

- **Dinero**: centavos enteros `i64`. **Tipos de cambio**: micros. **Tasas**: basis points. **Fechas de negocio**: TEXT `YYYY-MM-DD`.
- serde `rename_all = "camelCase"` en todo lo que cruza HTTP.
- Strings de UI SOLO en `src/i18n/` (bilingüe es/en); nada hardcodeado en componentes. `es.ts` es la forma canónica (`esDict`); al agregar una clave, agrégala también en `en.ts`. Los componentes importan `es` (un proxy al idioma activo) y usan `es.x`; el idioma se cambia en Ajustes (`src/i18n/store.ts`) y el router se remonta al cambiar. OJO: textos a nivel de módulo (fuera de un componente) quedan congelados al idioma inicial — defínelos dentro del componente.
- **Cambios de esquema**: solo vía migración nueva en `worker/migrations/*.sql` + actualizar `docs/DATA_MODEL.md`.
- **Calculadoras de inversión nuevas**: implementar `InvestmentCalculator` en finanzas-core + registry + tests + cargar su `CalcContext` en ambos loaders (worker y src-tauri) + form; guía en `docs/INVESTMENTS.md`.
- Git: conventional commits. TODO cambio de producto (cualquier `feat`, o una serie
  de commits relacionados) se desarrolla en branch `feat/<nombre>` y llega a `main`
  SOLO vía release: commit `chore(release)` (bump en `package.json` + entrada en
  `src/lib/changelog.ts`) + merge `--no-ff` + tag semver. Directo a `main` únicamente
  un `fix`/`chore`/`docs` trivial y aislado (un commit); un `feat` directo a `main`
  NUNCA. Solo se deploya un tag: si `main` tiene trabajo sin taguear, primero se
  corta el release. Regla de oro: `main` siempre = último release.
- Tests de fórmulas financieras obligatorios, con valores de referencia calculados a mano.

## Estado actual

- **v2.0.0 (feat/mobile)**: migración a la nube — PWA + Workers (workers-rs) +
  D1 multiusuario. La DB local `~/.local/share/com.asura.finanzas/finanzas.db`
  es respaldo de solo lectura post-migración: NUNCA borrarla ni escribirla.
  Migración de datos: `scripts/migrate_to_d1.py` (checksums antes/después).
- Antes — v1.6.x escritorio: BONDDIA con precio oficial + serie histórica,
  criptomonedas (CoinGecko), catálogo de inversiones con tasas Banxico sin
  token, DateInput/ConfirmDialog propios (WebKitGTK), tipos de cambio
  automáticos. Historia completa en `docs/DECISIONS.md` y `docs/PLAN.md`.
- Trabajo futuro: ver `docs/ROADMAP.md`.

## Docs

`docs/PLAN.md` (plan + checklist) · `docs/ARCHITECTURE.md` (capas, catálogo de comandos RPC, dev local) · `docs/DATA_MODEL.md` (SQL canónico de D1) · `docs/INVESTMENTS.md` (fórmulas: Nu cajita ACT/365 compuesto diario, CETES ACT/360 + ISR) · `docs/ROADMAP.md` · `docs/DECISIONS.md` (ADRs).
