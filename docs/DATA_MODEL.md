# Modelo de datos

Esquema canónico de SQLite. Cualquier cambio se hace **solo** vía una migración nueva en `src-tauri/src/db/mod.rs` y se refleja aquí.

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
CREATE TABLE schema_migrations (
  version INTEGER PRIMARY KEY,
  applied_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE currencies (
  code TEXT PRIMARY KEY,                  -- ISO 4217: 'MXN','USD'
  name TEXT NOT NULL,                     -- 'Peso mexicano'
  symbol TEXT NOT NULL,                   -- '$'
  decimals INTEGER NOT NULL DEFAULT 2
);

CREATE TABLE exchange_rates (             -- la fila más reciente por moneda gana
  id INTEGER PRIMARY KEY,
  currency_code TEXT NOT NULL REFERENCES currencies(code),
  rate_to_mxn_micros INTEGER NOT NULL,    -- 1 unidad = rate/1e6 MXN
  as_of TEXT NOT NULL DEFAULT (datetime('now')),
  source TEXT NOT NULL DEFAULT 'manual'   -- 'manual' | 'api' (futuro)
);

CREATE TABLE wallet_categories (
  id INTEGER PRIMARY KEY,
  name TEXT NOT NULL,
  icon TEXT,
  is_system INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE wallets (
  id INTEGER PRIMARY KEY,
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
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX idx_tx_wallet ON transactions(wallet_id, occurred_at);
CREATE INDEX idx_tx_transfer ON transactions(transfer_group_id);

CREATE TABLE investments (
  id INTEGER PRIMARY KEY,
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

CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT NOT NULL);
```

## Seeds (migración 2)

- **Monedas**: MXN (Peso mexicano), USD (Dólar estadounidense).
- **Categorías de cartera**: Efectivo, Tarjeta de débito, Tarjeta de crédito, Cuenta de ahorro, Inversión, Otro (`is_system = 1`).
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

- Una transferencia lógica = **2 filas** (`transfer_out` en origen, `transfer_in` en destino) con el mismo `transfer_group_id` (UUIDv4), insertadas dentro de una sola transacción SQL (`BEGIN`/`COMMIT`).
- Cross-currency: cada pierna lleva `amount_cents` en la moneda de su propia cartera; el usuario captura ambos montos.
- Borrar cualquiera de las piernas borra ambas (se busca por `transfer_group_id`).
