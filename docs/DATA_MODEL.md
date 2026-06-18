# Modelo de datos

Esquema canónico de SQLite **en Cloudflare D1**. Cualquier cambio se hace
**solo** vía una migración nueva en `worker/migrations/*.sql` (`wrangler d1
migrations apply finanzas`) y se refleja aquí.

> El esquema histórico del escritorio (`src-tauri/src/db/mod.rs`) quedó
> congelado: `~/.local/share/com.asura.finanzas/finanzas.db` es el respaldo de
> solo lectura previo a la migración a la nube (2026-06). No recibe cambios.

## Multiusuario (v2.0.0)

```sql
CREATE TABLE users (
  id INTEGER PRIMARY KEY,                 -- id 0 = usuario sistema (cachés globales)
  email TEXT NOT NULL UNIQUE COLLATE NOCASE,
  password_hash TEXT NOT NULL,            -- pbkdf2-sha256$<iters>$<salt_hex>$<hash_hex>; '!' = sin contraseña (solo Google)
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  google_sub TEXT                         -- 0004: OIDC 'sub' de Google (UNIQUE parcial)
);
CREATE UNIQUE INDEX idx_users_google_sub ON users(google_sub) WHERE google_sub IS NOT NULL;

CREATE TABLE sessions (
  id INTEGER PRIMARY KEY,
  user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash TEXT NOT NULL UNIQUE,        -- SHA-256 del token de la cookie
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  expires_at TEXT NOT NULL,               -- deslizante, +30 días al usarse
  user_agent TEXT,                        -- 0003: lista de dispositivos
  last_seen_at TEXT                       -- 0003: actividad (granularidad ~1h)
);

CREATE TABLE auth_attempts (             -- 0010: throttle anti-fuerza-bruta de /api/auth
  scope TEXT NOT NULL,                    -- 'login' | 'register'
  client_key TEXT NOT NULL,              -- CF-Connecting-IP (o 'unknown')
  count INTEGER NOT NULL,                 -- contador de ventana fija
  window_start TEXT NOT NULL,            -- datetime('now') al abrir la ventana
  PRIMARY KEY (scope, client_key)        -- upsert atómico; el cron diario poda filas viejas
);
```

Scoping: `wallets.user_id` y `investments.user_id` (NOT NULL);
`transaction_categories.user_id` (NULL = seed del sistema, visible a todos);
`transactions`, `investment_snapshots` e `investment_movements` se escopan por
JOIN a su padre; `settings` tiene PK `(user_id, key)` y el usuario 0 guarda
cachés globales (`bonddia_price`). `exchange_rates` usa el mismo patrón que
`settings` (user_id 0 = tasas globales del cron; >0 = override manual del
usuario). Tablas globales sin user_id: `currencies`, `wallet_categories`,
`rate_history`, `crypto_prices`.

## Convenciones

| Dato | Representación | Ejemplo |
|---|---|---|
| Dinero | centavos enteros (`INTEGER` / `i64`) | $1,234.56 → `123456` |
| Tipo de cambio | micros (1 unidad = rate/1e6 MXN) | 18.50 MXN/USD → `18500000` |
| Tasas / porcentajes | basis points (en `params_json`) | 12.50 % → `1250` |
| Fechas de negocio | TEXT `YYYY-MM-DD` | `2026-06-10` |
| Timestamps | TEXT ISO vía `datetime('now')` | `2026-06-10 17:30:00` |

Nunca se usan flotantes para dinero almacenado. `PRAGMA foreign_keys = ON` siempre.

## Esquema

```sql
-- (las migraciones las versiona wrangler en la tabla d1_migrations)

CREATE TABLE currencies (
  code TEXT PRIMARY KEY,                  -- ISO 4217: 'MXN','USD'
  name TEXT NOT NULL,                     -- 'Peso mexicano'
  symbol TEXT NOT NULL,                   -- '$'
  decimals INTEGER NOT NULL DEFAULT 2
);

CREATE TABLE exchange_rates (             -- por moneda: la tasa propia del usuario gana, si no la global
  id INTEGER PRIMARY KEY,
  currency_code TEXT NOT NULL REFERENCES currencies(code),
  rate_to_mxn_micros INTEGER NOT NULL,    -- 1 unidad = rate/1e6 MXN
  as_of TEXT NOT NULL DEFAULT (datetime('now')),
  source TEXT NOT NULL DEFAULT 'manual',  -- 'manual' (override del usuario) | 'api' | 'banxico_fix'
  user_id INTEGER NOT NULL DEFAULT 0      -- 0011: 0 = global (cron); >0 = override manual de ese usuario
);
-- Lectura (load_rates / get_exchange_rates): ROW_NUMBER() OVER (PARTITION BY
-- currency_code ORDER BY (user_id = ?uid) DESC, id DESC) — la fila manual del
-- usuario vence a la global 0; sin override, gana la global más reciente.

CREATE TABLE wallet_categories (
  id INTEGER PRIMARY KEY,
  name TEXT NOT NULL,
  icon TEXT,
  is_system INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE wallets (
  id INTEGER PRIMARY KEY,
  user_id INTEGER NOT NULL REFERENCES users(id),
  name TEXT NOT NULL,
  category_id INTEGER NOT NULL REFERENCES wallet_categories(id),
  currency_code TEXT NOT NULL REFERENCES currencies(code),
  initial_balance_cents INTEGER NOT NULL DEFAULT 0,
  color TEXT,
  notes TEXT,
  is_archived INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE transaction_categories (
  id INTEGER PRIMARY KEY,
  user_id INTEGER REFERENCES users(id),   -- NULL = seed del sistema (todos la ven)
  name TEXT NOT NULL,
  kind TEXT NOT NULL CHECK (kind IN ('income','expense')),
  icon TEXT,
  color TEXT,
  is_system INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE transactions (
  id INTEGER PRIMARY KEY,
  wallet_id INTEGER NOT NULL REFERENCES wallets(id),
  kind TEXT NOT NULL CHECK (kind IN ('income','expense','transfer_in','transfer_out')),
  amount_cents INTEGER NOT NULL CHECK (amount_cents > 0),  -- siempre positivo; kind da el signo
  category_id INTEGER REFERENCES transaction_categories(id),
  transfer_group_id TEXT,                 -- UUIDv4 compartido por las 2 piernas de una transferencia
  description TEXT,
  occurred_at TEXT NOT NULL,              -- 'YYYY-MM-DD'
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  client_id TEXT                          -- 0003: idempotencia del outbox offline
);
CREATE INDEX idx_tx_wallet ON transactions(wallet_id, occurred_at);
CREATE INDEX idx_tx_transfer ON transactions(transfer_group_id);
CREATE UNIQUE INDEX idx_tx_client ON transactions(client_id) WHERE client_id IS NOT NULL;

CREATE TABLE investments (
  id INTEGER PRIMARY KEY,
  user_id INTEGER NOT NULL REFERENCES users(id),
  name TEXT NOT NULL,
  calculator TEXT NOT NULL,               -- 'nu_cajita' | 'cetes' | 'fixed_rate' | 'manual'
  currency_code TEXT NOT NULL DEFAULT 'MXN' REFERENCES currencies(code),
  principal_cents INTEGER NOT NULL,
  start_date TEXT NOT NULL,               -- 'YYYY-MM-DD'
  params_json TEXT NOT NULL DEFAULT '{}', -- específico de cada calculadora (ver INVESTMENTS.md)
  linked_wallet_id INTEGER REFERENCES wallets(id),
  is_closed INTEGER NOT NULL DEFAULT 0,
  notes TEXT,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE investment_snapshots (       -- marcas manuales + registro histórico
  id INTEGER PRIMARY KEY,
  investment_id INTEGER NOT NULL REFERENCES investments(id) ON DELETE CASCADE,
  value_cents INTEGER NOT NULL,
  as_of TEXT NOT NULL,
  source TEXT NOT NULL DEFAULT 'manual'
);

CREATE TABLE settings (                  -- user_id 0 = cachés globales (bonddia_price)
  user_id INTEGER NOT NULL REFERENCES users(id),
  key TEXT NOT NULL, value TEXT NOT NULL, PRIMARY KEY (user_id, key)
);

CREATE TABLE investment_movements (   -- aportaciones y retiros posteriores al inicio
  id INTEGER PRIMARY KEY,
  investment_id INTEGER NOT NULL REFERENCES investments(id) ON DELETE CASCADE,
  kind TEXT NOT NULL CHECK (kind IN ('deposit','withdrawal')),
  amount_cents INTEGER NOT NULL CHECK (amount_cents > 0),
  occurred_at TEXT NOT NULL,          -- 'YYYY-MM-DD', >= start_date de la inversión
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX idx_inv_mov ON investment_movements(investment_id, occurred_at);
```

## Semántica de movimientos de inversión

- El `principal_cents` de la inversión es la aportación inicial (en `start_date`); los movimientos posteriores viven en `investment_movements`.
- **Valuación por posición**: cada monto genera rendimiento desde su propia fecha — `valor(t) = principal·f(start→t) + Σ aportaciones·f(fecha→t) − Σ retiros·f(fecha→t)`, donde `f` es el factor de crecimiento de la calculadora. Un retiro resta también el rendimiento que ese dinero habría generado desde la fecha del retiro.
- **Aportado neto** = principal + aportaciones − retiros. **Rendimiento** = valor actual − aportado neto (captura rendimiento realizado y no realizado; puede ser positivo aunque el valor actual sea menor a lo retirado).
- Las inversiones `manual` no usan movimientos (su valor viene de snapshots).

## Seeds (worker/migrations/0002_seed.sql)

- **Monedas**: MXN (Peso mexicano), USD (Dólar estadounidense).
- **Categorías de cartera**: Efectivo, Tarjeta de débito, Tarjeta de crédito, Cuenta de ahorro, Otro (`is_system = 1`; la categoría 'Inversión' del seed original del escritorio ya no existe).
- **Categorías de transacción** — income: Salario, Regalo, Intereses, Otro ingreso; expense: Comida, Transporte, Hogar, Entretenimiento, Salud, Suscripciones, Otro gasto (`is_system = 1`).

## Regla de saldo (computado, nunca almacenado)

```sql
SELECT w.initial_balance_cents + COALESCE(SUM(
  CASE t.kind WHEN 'income'      THEN t.amount_cents
              WHEN 'transfer_in' THEN t.amount_cents
              ELSE -t.amount_cents END), 0)
FROM wallets w LEFT JOIN transactions t ON t.wallet_id = w.id
WHERE w.id = ?1;
```

## Semántica de transferencias

- Una transferencia lógica = **2 filas** (`transfer_out` en origen, `transfer_in` en destino) con el mismo `transfer_group_id` (UUIDv4), insertadas en un `batch()` de D1 (su única primitiva transaccional; atómico).
- Cross-currency: cada pierna lleva `amount_cents` en la moneda de su propia cartera; el usuario captura ambos montos.
- Borrar cualquiera de las piernas borra ambas (se busca por `transfer_group_id`).

## Cachés de mercado

```sql
CREATE TABLE rate_history (      -- serie histórica de Banxico (hoy: 'objetivo' para bonddia)
  series TEXT NOT NULL, date TEXT NOT NULL, rate_bps INTEGER NOT NULL,
  PRIMARY KEY (series, date)
);
CREATE TABLE crypto_prices (     -- último precio por símbolo (CoinGecko)
  symbol TEXT PRIMARY KEY, price_mxn_cents INTEGER NOT NULL,
  price_usd_cents INTEGER, as_of TEXT NOT NULL DEFAULT (datetime('now'))
);
```
Ambas se refrescan con el cron trigger diario del worker y bajo demanda con `refresh_market_data_cmd`.

## Metas, presupuestos y suscripciones (migración 0005)

Todo por usuario (`user_id`). El dinero se agrega/convierte a MXN en Rust; el avance va en basis points calculados en el handler.

```sql
CREATE TABLE savings_goals (     -- meta con monto ahorrado manual; progreso = saved/target
  id INTEGER PRIMARY KEY, user_id INTEGER NOT NULL REFERENCES users(id),
  name TEXT NOT NULL, icon TEXT, color TEXT,
  currency_code TEXT NOT NULL DEFAULT 'MXN' REFERENCES currencies(code),
  target_cents INTEGER NOT NULL CHECK (target_cents > 0),
  saved_cents INTEGER NOT NULL DEFAULT 0 CHECK (saved_cents >= 0),
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE budgets (           -- límite mensual; category_id NULL = límite general
  id INTEGER PRIMARY KEY, user_id INTEGER NOT NULL REFERENCES users(id),
  category_id INTEGER REFERENCES transaction_categories(id),
  limit_cents INTEGER NOT NULL CHECK (limit_cents > 0),
  period TEXT NOT NULL DEFAULT 'monthly' CHECK (period IN ('monthly')),
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
-- unicidad por (usuario, categoría) incluyendo el general (NULL→0)
CREATE UNIQUE INDEX idx_budgets_unique ON budgets(user_id, COALESCE(category_id, 0));

CREATE TABLE subscriptions (     -- pagos recurrentes; "registrar pago" inserta un gasto y avanza next_charge_date
  id INTEGER PRIMARY KEY, user_id INTEGER NOT NULL REFERENCES users(id),
  name TEXT NOT NULL, icon TEXT, color TEXT,
  amount_cents INTEGER NOT NULL CHECK (amount_cents > 0),
  currency_code TEXT NOT NULL DEFAULT 'MXN' REFERENCES currencies(code),
  cadence TEXT NOT NULL DEFAULT 'monthly' CHECK (cadence IN ('monthly','yearly')),
  next_charge_date TEXT NOT NULL,
  wallet_id INTEGER REFERENCES wallets(id),
  category_id INTEGER REFERENCES transaction_categories(id),
  is_active INTEGER NOT NULL DEFAULT 1,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
```

- **budgets.spent** nunca se almacena: se calcula al leer (gasto del mes en MXN, general o por categoría).
- **subscriptions** total mensual = suma de activas normalizadas a mes (anual/12) en MXN.

- **wallets.skin** (migración 0006): estilo de tarjeta — id de catálogo, `grad:<from>,<to>,<angle>` o `img:<data-url>`; NULL = derivado del color.

- **categorías (migración 0009)**: cada `transaction_categories` trae un `color` por defecto distinto (seeds asignados por nombre; resto por `id % paleta`; ver `CATEGORY_PALETTE` en `src/lib/palette.ts`). Orden de despliegue por usuario en `category_order(user_id, category_id, position)` (PK compuesta): el manager lo reordena arrastrando; los listados hacen LEFT JOIN y ordenan por `kind, (position IS NULL), position, is_system DESC, id`. Por-usuario, así que reordenar seeds compartidos no afecta a otros. RPC `reorder_transaction_categories`.

- **wallets.sort_order** (migración 0007): orden de despliegue definido por el usuario (arrastrar para reordenar). Menor = primero; empates por `created_at, id`. Carteras nuevas van al final (`MAX(sort_order)+1`). `reorder_wallets` reescribe el `sort_order` de cada id según su índice (batch atómico, scoped por usuario).
