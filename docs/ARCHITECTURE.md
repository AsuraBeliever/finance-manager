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

- `users` + `sessions` (token de cookie hasheado SHA-256, expiración deslizante
  30 días; guardan `user_agent` y `last_seen_at` para la lista de dispositivos).
- Registro con código de invitación (secret `INVITE_CODE`).
- Cuenta (`/api/auth/*`): `register`, `login`, `logout`, `me`, `sessions`
  (dispositivos activos), `revoke_session`, `revoke_other_sessions`,
  `change_password` (exige la actual y revoca las demás sesiones).
- **Google** (`/api/auth/google/{start,callback}`): OAuth 2.0 Authorization
  Code server-side. `start` setea cookie CSRF `oauth_state` y redirige a la
  pantalla de Google; `callback` valida el state, intercambia el code por el
  `id_token` (TLS directo con el client_secret ⇒ no se verifica la firma),
  y crea/vincula el usuario por `google_sub`/email verificado. Abierto (sin
  invitación); usuarios solo-Google tienen `password_hash = '!'`. Requiere
  `GOOGLE_CLIENT_ID` (var) y `GOOGLE_CLIENT_SECRET` (secret).
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
| Tarjetas de crédito | `get_credit_card_summary` (deuda, saldo al corte, por pagar, fecha límite, utilización, crédito disponible, MSI), `preview_msi_plan` (calendario en vivo para el form), `create_msi_plan` (devuelve el mismo calendario para la confirmación), `delete_msi_plan` — lógica de calendario en `finanzas-core::credit`; el cron postea las mensualidades MSI |
| Transacciones | `add_income`, `add_expense`, `add_refund` (dinero que regresa por un gasto; `refundOfId` opcional lo liga al gasto — ver DATA_MODEL «Reembolsos»), `add_transfer` (los cuatro aceptan `clientId` opcional para idempotencia del outbox offline), `list_transactions` (cada fila trae `refundOfId`, `refundedCents`, `refundableCents`; `kind` = `refund` en reembolsos), `sum_transactions` (total de lo filtrado: `count`, `totalMxnCents` y `byCurrency`; sólo con `kind` = `income`, `expense` o `refund`), `delete_transaction`, `list_transaction_categories`, `create_transaction_category` — el `filter` de los dos primeros acepta `walletId`, `kind`, `categoryId`, `from`/`to` y `period` (misma ventana que analytics, resuelta en `finanzas-core::period`) |
| Dashboard | `get_dashboard_summary` (foto del momento: saldos, donas, inversiones) |
| Analytics | `get_spending_trends`, `get_category_breakdown` — ambos aceptan `period` (`{kind}`: `allTime` \| `currentMonth` \| `lastMonths{months}` \| `month{year,month}` \| `day{date}` \| `range{from,to}`); la ventana se resuelve en `finanzas-core::period` |
| Inversiones | `list_investments`, `create_investment`, `update_investment`, `close_investment`, `delete_investment`, `get_investment_detail`, `add_snapshot`, `add_investment_movement`, `get_investment_movement` (por id o por su transacción), `update_investment_movement`, `delete_investment_movement`, `list_calculators`, `get_investment_catalog` |
| Avisos del banco (FIN-5) | `get_bank_notice_settings` (Gmail conectado o no, bancos incluidos, remitentes y tarjetas del usuario), `connect_gmail_code` (Android: server auth code → token), `disconnect_gmail` (para el watch, revoca y borra), `add_bank_sender` / `update_bank_sender` / `delete_bank_sender`, `set_card_wallet` (`walletId` null = quitar), `list_pending_transactions` (con cartera/categoría sugeridas y `duplicateOf`), `confirm_pending_transaction` (registra un ingreso/gasto normal con `client_id = 'pending-<id>'`), `dismiss_pending_transaction`, `import_bank_notice` (pegar texto) |
| Ajustes / mercado | `list_currencies`, `add_currency`, `get_exchange_rates`, `set_exchange_rate`, `fetch_exchange_rates`, `fetch_banxico_rate`, `refresh_market_data_cmd`, `get_setting`, `set_setting` |

Datos de mercado: cron trigger 3× al día (07:00, 15:00 y 20:00 UTC = 01:00,
09:00 y 14:00 CDMX) refresca fx, historial de tasa objetivo, precio BONDDIA y
precios cripto; también borra sesiones expiradas. Fallos silenciosos, visibles
con `wrangler tail`. Las corridas tardías existen por BONDDIA: a la 01:00 la
página de cetesdirecto todavía muestra la fecha (y el «precio día anterior») del
día previo, así que con una sola corrida la posición en títulos iba siempre un
día de rendimiento atrás del app de cetesdirecto.

Un cuarto trigger, `*/5 * * * *` (despachado por cadena en `lib.rs`,
`SUBSCRIPTION_CRON`), renueva los watches de Gmail por caducar (ver «Avisos del
banco por Gmail») y cobra las suscripciones con `auto_charge = 1`
(`subscriptions::post_due_subscriptions`): un gasto por cada fecha de cargo
vencida (fechada en esa fecha, no en la del cron; hasta 12 de rezago). El cargo
de hoy espera a su `charge_time` (CDMX), así que sale a lo más 5 min después de
la hora elegida. `client_id = 'sub:<id>:<fecha>'` + guarda sobre
`next_charge_date` para que corridas traslapadas no dupliquen. Las suscripciones manuales sólo se cobran con
«Registrar pago», que fecha el gasto el día en que se presiona.

## Offline (PWA)

Dos mecanismos, ambos del lado de la app (el service worker sigue sin cachear
`/api/*`):

- **Consulta**: la caché de TanStack Query se persiste en localStorage
  (`PersistQueryClientProvider`, `maxAge` 7 días, llave `finanzas.cache.v1`).
  Sin red, la app abre con los últimos datos sincronizados y un banner visible
  de «sin conexión»; el servidor sigue siendo la única fuente de verdad.
- **Captura (outbox)**: `src/lib/outbox.ts` encola SOLO
  `add_income`/`add_expense`/`add_refund`/`add_transfer` (append-only ⇒ sin conflictos) en
  localStorage con un `clientId` que el servidor usa para idempotencia (índice
  único en `transactions.client_id`): un reenvío tras una respuesta perdida
  jamás duplica. La cola se drena al evento `online` y al arrancar; los items
  con error de API quedan marcados para revisión en el panel «Pendientes de
  sincronizar» (TransactionsPage), separados de la lista real — no afectan
  saldos hasta sincronizarse.

La query `["me"]` usa `refetchOnMount: "always"` (no `staleTime: Infinity`):
tras un redirect de OAuth la app debe revalidar la sesión contra el servidor;
la caché persistida igual da render instantáneo y se conserva si la red falla.

## Actualizaciones (un deploy actualiza todo)

El escritorio es un shell Tauri que carga la URL desplegada, igual que la web y
la PWA de iPhone — los tres corren el mismo bundle servido por el Worker, así
que **`wrangler deploy` actualiza a los tres**. No se recompila el binario
Tauri salvo cambios en código nativo de `src-tauri/` (dormido).

La PWA usa `registerType: "prompt"` (vite-plugin-pwa). `src/features/update/
UpdateBanner.tsx` (hook `useRegisterSW`) muestra «Hay una nueva versión —
Actualizar» cuando hay un service worker nuevo en espera; el botón llama
`updateServiceWorker(true)` (activa el SW nuevo y recarga). Re-chequea cada
hora y al volver el foco, para que el shell de escritorio (siempre abierto)
note las versiones sin recargar a mano.

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

## Avisos del banco por Gmail (push)

Sin dominio propio ni sondeo: al conectar, el worker llama `users.watch` de la
Gmail API con el tema de Pub/Sub `GMAIL_PUBSUB_TOPIC`; la suscripción push de ese
tema hace `POST /api/gmail/push?token=<GMAIL_PUSH_TOKEN>` en cuanto cambia el
buzón. El worker lee `history.list` desde el `history_id` guardado, mira solo
los encabezados y etiquetas de cada correo nuevo (salta lo que Gmail clasificó
como Promociones o spam) y abre completo únicamente el de un remitente que es
banco (BBVA/Nu incluidos + `bank_senders`); el lector de
`finanzas-core::bank_notice` lo convierte en una fila de
`pending_transactions`. Siempre responde 204 tras autenticar: si algo falla, el
cursor no avanza y el siguiente push lo recoge.

Límites del plan gratuito (50 subrequests y 50 consultas D1 por invocación):
cada push lee **una página de 10 registros** de history como máximo y el cursor
avanza registro por registro (un registro cuenta como leído cuando todos sus
correos lo están); lo que no cabe lo lee el siguiente push. Los remitentes del
usuario se leen una vez por push. Cada conexión se procesa aparte: si una
falla, las demás siguen. El watch dura 7 días: el cron de cada 5 minutos
(`SUBSCRIPTION_CRON`) renueva como mucho 3 por corrida, los que tienen menos de
2 días de vida, el más próximo a caducar primero.

- Conexión web: `GET /api/gmail/connect` (requiere sesión) → Google → el mismo
  `/api/auth/google/callback` del login, distinguido por el `state` `gmail.…`.
- Conexión Android: `AuthorizationClient` → server auth code → `connect_gmail_code`.
- Tokens en D1 cifrados con AES-GCM (SubtleCrypto) con el secreto `GMAIL_TOKEN_KEY`.
- Secretos/vars: `GMAIL_TOKEN_KEY` (base64 de 32 bytes), `GMAIL_PUSH_TOKEN`,
  `GMAIL_PUBSUB_TOPIC`; `GMAIL_API_BASE` / `GOOGLE_TOKEN_URL` solo para pruebas
  locales contra un Google falso. Sin `GMAIL_TOKEN_KEY` la app ofrece solo «Pegar aviso».
- Requiere en Google Cloud: Gmail API y Pub/Sub habilitadas, permiso de publicar
  en el tema para `gmail-api-push@system.gserviceaccount.com`, y el scope
  `gmail.readonly` en la pantalla de consentimiento (restringido: hasta pasar la
  verificación de Google, solo usuarios de prueba).

## Rutas de UI

| Ruta | Pantalla |
|---|---|
| (sin sesión) | Login / registro con código de invitación |
| `/` | Resumen: patrimonio total MXN, desglose por moneda, dona por cartera, gráfica de flujo con selector de periodo (todo el tiempo / mes actual / últimos X meses / un mes / un día / rango), card de inversiones |
| `/carteras`, `/carteras/:id` | Lista + detalle con historial de transacciones |
| `/transacciones` | Lista con filtros; modal con tabs ingreso/gasto/transferencia |
| `/inversiones`, `/inversiones/:id` | Lista + detalle con gráfica de proyección y vencimiento |
| `/avisos` | Avisos del banco: bandeja para confirmar/descartar, pegar aviso, Gmail, bancos y tarjetas |
| `/ajustes` | Monedas, categorías, sesión (cerrar sesión en móvil) |
