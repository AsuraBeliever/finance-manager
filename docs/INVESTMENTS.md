# Motor de inversiones

Vive completo en Rust (`src-tauri/src/investments/`). El frontend solo renderiza valores y series que regresa el backend.

## Trait y registry

```rust
pub trait InvestmentCalculator: Send + Sync {
    fn id(&self) -> &'static str;
    /// Valor en centavos en la fecha `as_of` (fechas futuras = proyección).
    fn value_at(&self, inv: &Investment, as_of: NaiveDate) -> Result<i64, CalcError>;
    fn maturity_date(&self, inv: &Investment) -> Option<NaiveDate>;
}

pub fn registry() -> &'static [&'static dyn InvestmentCalculator];
// nu_cajita, cetes, fixed_rate, manual
```

Cada inversión guarda `calculator` (id del trait) y `params_json` (parámetros específicos, tasas en basis points).

## Calculadoras

### `nu_cajita` — Cajitas de Nu México

Params: `{"annual_rate_bps": 1500}` (editable: Nu cambia la tasa con el tiempo).

Interés compuesto diario, convención **ACT/365** (Nu publica tasa anual con rendimientos diarios):

```rust
let r = annual_rate_bps as f64 / 10_000.0;
let days = (as_of - inv.start_date).num_days().max(0);
let value = inv.principal_cents as f64 * (1.0 + r / 365.0).powi(days as i32);
value.round() as i64
```

Sin vencimiento (`maturity_date = None`).

### `cetes` — CETES (cetesdirecto)

Params: `{"annual_rate_bps": 1080, "plazo_days": 91, "isr_rate_bps": 50}`.
Plazos válidos: 28, 91, 182, 364. `isr_rate_bps = 0` desactiva la retención.

Los CETES son instrumentos cupón cero: se compran a descuento y pagan valor nominal ($10.00 por título) al vencimiento. Convención de mercado de dinero **ACT/360**; la retención de ISR se prorratea **ACT/365** sobre el capital:

```rust
let r = annual_rate_bps as f64 / 10_000.0;
let d = (as_of - start).num_days().clamp(0, plazo_days) as f64;
let gross = principal_cents as f64 * (1.0 + r * d / 360.0);
let isr   = principal_cents as f64 * (isr_rate_bps as f64 / 10_000.0) * d / 365.0;
(gross - isr).round() as i64
```

Consistencia: `principal = títulos × 10 / (1 + r·plazo/360)`, de modo que al vencimiento `principal × (1 + r·plazo/360) = títulos × $10.00` nominal.

`maturity_date = start_date + plazo_days`. El detalle muestra valor al vencimiento, rendimiento neto y fecha de vencimiento.

Ejemplo de referencia (test): $10,000.00 al 10.80 % a 91 días sin ISR → `10000 × (1 + 0.108 × 91/360)` = **$10,273.00** brutos al vencimiento.

### `fixed_rate` — tasa personalizada

Params: `{"annual_rate_bps": N, "compounding": "daily" | "monthly" | "simple"}`.

- `daily`: igual que nu_cajita generalizado, `(1 + r/365)^días`.
- `monthly`: `(1 + r/12)^meses` con meses = días/30.44 truncado… (definir exacto en implementación con chrono, meses calendario completos).
- `simple`: `principal × (1 + r × días/365)`.

### `manual` — valor manual

Params: `{}`. `value_at` regresa el último `investment_snapshots.value_cents` con `as_of ≤ fecha`; si no hay snapshots, regresa `principal_cents`. El usuario actualiza el valor agregando snapshots.

## Cómo agregar una calculadora nueva

1. Crear `src-tauri/src/investments/<nombre>.rs` implementando `InvestmentCalculator`.
2. Registrarla en el array de `registry()` en `src-tauri/src/investments/mod.rs`.
3. Agregar tests unitarios `#[cfg(test)]` con valores de referencia calculados a mano.
4. Agregar la variante del formulario en el frontend (`src/features/investments/`) y sus strings en `src/i18n/es.ts`.
5. Documentar la fórmula aquí.

## Proyecciones

`get_investment_detail(id)` regresa valor actual, ganancia (valor − principal) y una serie `[{date, value_cents}]` con puntos semanales hasta el vencimiento (o +1 año si no hay vencimiento) para la gráfica de línea en Recharts.
