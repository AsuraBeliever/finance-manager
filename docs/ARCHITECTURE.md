# Arquitectura

Desde v2.0.0 la app vive en Cloudflare: un solo Worker sirve el frontend (PWA)
y el API; los datos están en D1. El escritorio es un shell Tauri que carga la
URL desplegada. El iPhone la instala desde Safari («Agregar a pantalla de
inicio»).

## Stack

| Capa | Tecnología |
|---|---|
| Backend | Cloudflare Workers — **workers-rs** (Rust → WASM), crate `worker/` |
| Base de datos | Cloudflare D1 (SQLite serverless), migraciones en `worker/migrations/` |
| Lógica financiera | `crates/finanzas-core` — pura, sin deps de almacenamiento, tests nativos |
| Frontend | React 19 + TypeScript 5 + Vite 7, PWA vía `vite-plugin-pwa` |
| Routing | React Router 7 (`createHashRouter` — funciona igual en web y Tauri sin SPA-fallback) |
| Estado de servidor | TanStack Query 5 (cada llamada RPC envuelta en query/mutation) |
| Estilos | Tailwind CSS 4 (`@tailwindcss/vite`, tema en `src/index.css`) |
| Gráficas / íconos / fechas | Recharts 3 · lucide-react · date-fns 4 (`es`) |
| Shell de escritorio | Tauri 2 (solo ventana; el backend local quedó dormido tras la migración) |

## Capas

```
React (render + forms)  ──  PWA instalable (Safari/Chrome)
  └── src/lib/api.ts          rpc<T>(name, args): POST /api/rpc/<name>
  └── src/lib/auth.ts         /api/auth/{register,login,logout,me}
        └── Cloudflare Worker (worker/src/)
              ├── rpc.rs            dispatcher por nombre + sesión obligatoria
              ├── auth/             invite code, PBKDF2 (SubtleCrypto), cookies
              ├── handlers/*.rs     validación + SQL D1 (scoped por user_id)
              ├── market.rs         Fetch a Banxico/open.er-api/CoinGecko + cron
              └── finanzas-core     modelos, errores, calculadoras (CalcContext)
                    └── D1 (binding DB)
```

**Reglas de oro:**
- El frontend nunca calcula dinero: recibe centavos/series listas.
- Toda escritura multi-statement usa `db.batch()` — D1 no tiene BEGIN/COMMIT
  interactivo; un batch es la única transacción atómica.
- El service worker JAMÁS cachea `/api/*`.
- Aritmética de dinero en Rust; nunca multiplicar centavos×micros en SQL (los
  números de D1 cruzan a JS como f64, exactos solo bajo 2^53).

## Multiusuario

- `users` + `sessions` (token de cookie hasheado SHA-256, expiración deslizante 30 días).
- Registro con código de invitación (secret `INVITE_CODE`).
- `user_id` en `wallets`, `investments`, `transaction_categories` (NULL = seed
  del sistema visible a todos); `transactions`/`snapshots`/`movements` se
  escopan por JOIN al padre. `settings` tiene PK `(user_id, key)`; el usuario
  sistema (id 0) guarda cachés globales (p. ej. `bonddia_price`).
- Globales para todos: `currencies`, `exchange_rates`, `wallet_categories`,
  `rate_history`, `crypto_prices`.

## Catálogo de comandos RPC

`POST /api/rpc/<comando>`, cuerpo JSON camelCase (idéntico al payload del
puente Tauri original), respuesta JSON o `{"error": "..."}` (400/401/404/500).

| Área | Comandos |
|---|---|
| Carteras | `list_wallets`, `get_wallet`, `create_wallet`, `update_wallet`, `archive_wallet`, `delete_wallet`, `list_wallet_categories` |
| Transacciones | `add_income`, `add_expense`, `add_transfer`, `list_transactions`, `delete_transaction`, `list_transaction_categories`, `create_transaction_category` |
| Dashboard | `get_dashboard_summary` |
| Inversiones | `list_investments`, `create_investment`, `update_investment`, `close_investment`, `delete_investment`, `get_investment_detail`, `add_snapshot`, `add_investment_movement`, `delete_investment_movement`, `list_calculators`, `get_investment_catalog` |
| Ajustes / mercado | `list_currencies`, `add_currency`, `get_exchange_rates`, `set_exchange_rate`, `fetch_exchange_rates`, `fetch_banxico_rate`, `refresh_market_data_cmd`, `get_setting`, `set_setting` |

Datos de mercado: cron trigger diario (07:00 UTC ≈ 01:00 CDMX) refresca fx,
historial de tasa objetivo, precio BONDDIA y precios cripto; también borra
sesiones expiradas. Fallos silenciosos, visibles con `wrangler tail`.

## Estructura de carpetas

```
finanzas/
├── CLAUDE.md                  # conocimiento entre sesiones
├── Cargo.toml                 # workspace: finanzas-core, src-tauri, worker
├── docs/                      # esta documentación
├── crates/finanzas-core/      # modelos, errores, calculadoras, parsers de mercado + tests
├── worker/                    # Cloudflare Worker (workers-rs)
│   ├── wrangler.toml          # assets ../dist, binding DB, cron, run_worker_first /api/*
│   ├── migrations/            # esquema D1 canónico (ver DATA_MODEL.md)
│   └── src/                   # lib, rpc, auth/, handlers/, market, db, error
├── src/                       # frontend React (PWA)
│   ├── App.tsx                # guard de sesión + sidebar (md+) / bottom-nav (móvil)
│   ├── i18n/es.ts             # TODOS los strings de UI (español)
│   ├── lib/                   # api.ts (rpc), auth.ts, money.ts, types.ts
│   ├── components/            # PageHeader, EmptyState, Modal, DateInput, ...
│   └── features/              # auth/ dashboard/ wallets/ transactions/ investments/ settings/
├── scripts/migrate_to_d1.py   # export único finanzas.db → D1 (con checksums)
└── src-tauri/                 # shell de escritorio (carga la URL desplegada)
```

## Desarrollo local

```sh
npm run build                        # genera dist/ (el worker lo sirve)
cd worker && npx wrangler d1 migrations apply finanzas --local
cd worker && npx wrangler dev        # app completa en http://localhost:8787
npm run dev                          # (opcional) Vite con HMR; /api se proxea a :8787
```

`worker/.dev.vars` define `INVITE_CODE` para dev. En producción es un secret
(`wrangler secret put INVITE_CODE`).

## Rutas de UI

| Ruta | Pantalla |
|---|---|
| (sin sesión) | Login / registro con código de invitación |
| `/` | Resumen: patrimonio total MXN, desglose por moneda, dona por cartera, barras 6 meses, card de inversiones |
| `/carteras`, `/carteras/:id` | Lista + detalle con historial de transacciones |
| `/transacciones` | Lista con filtros; modal con tabs ingreso/gasto/transferencia |
| `/inversiones`, `/inversiones/:id` | Lista + detalle con gráfica de proyección y vencimiento |
| `/ajustes` | Monedas, categorías, sesión (cerrar sesión en móvil) |
