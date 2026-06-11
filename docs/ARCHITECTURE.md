# Arquitectura

## Stack

| Capa | Tecnología |
|---|---|
| Shell de escritorio | Tauri 2 |
| Frontend | React 19 + TypeScript 5 + Vite 7 |
| Routing | React Router 7 (`createHashRouter` — el protocolo custom de Tauri no reescribe rutas history) |
| Estado de servidor | TanStack Query 5 (cada `invoke` envuelto en query/mutation; invalidación en lugar de sync manual) |
| Estilos | Tailwind CSS 4 (`@tailwindcss/vite`, tema en `src/index.css`) |
| Gráficas | Recharts 3 |
| Íconos | lucide-react |
| Fechas (TS) | date-fns 4 con locale `es` |
| Base de datos | SQLite vía **rusqlite** (feature `bundled`) en comandos Rust |

## Capas

```
React (render + forms)
  └── src/lib/api.ts        wrappers tipados de invoke(), uno por comando
        └── Tauri IPC
              └── src-tauri/src/commands/*.rs   validación + orquestación
                    └── rusqlite (Mutex<Connection> en managed state)
                    └── src-tauri/src/investments/*.rs   motor de cálculo
```

**Regla de oro: el frontend nunca calcula dinero.** Toda agregación de saldos, conversión de divisas y cálculo de inversiones vive en Rust y regresa centavos enteros listos para formatear.

- Conexión: `Mutex<rusqlite::Connection>` único (app de escritorio, un usuario; no hace falta pool).
- Archivo: `app_data_dir()/finanzas.db` → `~/.local/share/com.asura.finanzas/finanzas.db`.
- Migraciones: SQL embebido ejecutado al arranque, registrado en `schema_migrations`.
- Errores: `AppError` (thiserror) serializado a string; el frontend lo muestra tal cual.
- Tipos de cambio: se actualizan solos al arrancar (task async en `setup`, silencioso si falla u offline) y bajo demanda desde Ajustes. Proveedor: open.er-api.com (gratuito, sin llave, tasas diarias). La edición manual sigue disponible; la fila más reciente gana sin importar la fuente. Regla: nunca sostener el lock de la DB a través de un `await`.
- Serialización: serde con `rename_all = "camelCase"` en todos los modelos que cruzan el puente.

## Catálogo de comandos Tauri

Todos regresan `Result<T, AppError>`.

| Área | Comandos |
|---|---|
| Carteras | `list_wallets` (con `balance_cents` computado), `get_wallet`, `create_wallet`, `update_wallet`, `archive_wallet`, `delete_wallet` (borra en cascada transacciones y pares de transferencia), `list_wallet_categories` |
| Transacciones | `add_income`, `add_expense`, `add_transfer`, `list_transactions` (filtros: wallet, kind, categoría, rango de fechas, paginado), `delete_transaction` (borra la pierna hermana de una transferencia), `list_transaction_categories`, `create_transaction_category` |
| Dashboard | `get_dashboard_summary` (saldos por cartera, subtotales por moneda, total MXN, total inversiones, ingresos/gastos 6 meses) |
| Inversiones | `list_investments`, `create_investment`, `update_investment`, `close_investment`, `get_investment_detail` (valor, ganancia, serie de proyección), `add_snapshot`, `list_calculators`, `get_investment_catalog` (async; catálogo con tasas vivas de Banxico) |
| Ajustes | `list_currencies`, `add_currency`, `get_exchange_rates`, `fetch_banxico_rate` (async), `refresh_market_data_cmd` (async; historial de tasa objetivo + precios cripto) |

## Estructura de carpetas

```
finanzas/
├── CLAUDE.md                  # conocimiento entre sesiones
├── docs/                      # esta documentación
├── src/                       # frontend React
│   ├── main.tsx               # QueryClientProvider + RouterProvider
│   ├── App.tsx                # layout con sidebar
│   ├── index.css              # Tailwind + tema
│   ├── i18n/es.ts             # TODOS los strings de UI (español)
│   ├── lib/                   # api.ts, money.ts, types.ts
│   ├── components/            # PageHeader, EmptyState, Modal, AmountInput, ...
│   └── features/              # dashboard/ wallets/ transactions/ investments/ settings/
└── src-tauri/
    └── src/
        ├── lib.rs             # setup: db, migraciones, managed state, registro de comandos
        ├── error.rs           # AppError
        ├── db/mod.rs          # conexión + migraciones embebidas
        ├── models.rs          # structs serde (camelCase)
        ├── commands/          # wallets, transactions, investments, dashboard, settings
        └── investments/       # mod (trait + registry), nu_cajita, cetes, fixed_rate, manual
```

## Rutas de UI

| Ruta | Pantalla |
|---|---|
| `/` | Resumen: patrimonio total MXN, desglose por moneda, dona por cartera, barras 6 meses, card de inversiones |
| `/carteras`, `/carteras/:id` | Lista + detalle con historial de transacciones |
| `/transacciones` | Lista con filtros; modal con tabs ingreso/gasto/transferencia |
| `/inversiones`, `/inversiones/:id` | Lista + detalle con gráfica de proyección y vencimiento |
| `/ajustes` | Tipos de cambio, monedas, categorías |
