import { getLocale } from "./store";

// System seed rows (wallet categories, transaction categories, currencies) are
// stored in the D1 database with Spanish names (see worker/migrations/0002_seed.sql).
// The UI is bilingual, so when the active locale is English we translate those
// canonical Spanish names on the fly. User-created rows are never in this map
// and fall through unchanged.
const EN: Record<string, string> = {
  // Wallet categories
  Efectivo: "Cash",
  "Tarjeta de débito": "Debit card",
  "Tarjeta de crédito": "Credit card",
  "Cuenta de ahorro": "Savings account",
  Otro: "Other",
  // Transaction categories — income
  Salario: "Salary",
  Regalo: "Gift",
  Intereses: "Interest",
  "Otro ingreso": "Other income",
  // Transaction categories — expense
  Comida: "Food",
  Transporte: "Transport",
  Hogar: "Home",
  Entretenimiento: "Entertainment",
  Salud: "Health",
  Suscripciones: "Subscriptions",
  "Otro gasto": "Other expense",
  // Currencies
  "Peso mexicano": "Mexican peso",
  "Dólar estadounidense": "US dollar",
};

/**
 * Localize the name of a system seed row. For user-created rows (`isSystem` false)
 * or under the Spanish locale the stored name is returned verbatim.
 */
export function seedName<T extends string | null | undefined>(
  name: T,
  isSystem = true,
): T {
  if (!name || !isSystem || getLocale() !== "en") return name;
  return (EN[name] ?? name) as T;
}
