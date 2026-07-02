// In-app changelog. Each release lists its user-facing changes (bilingual).
// Newest first. The "What's new" modal shows entries the user hasn't seen yet
// (auto on update, toggleable) and Settings can open the full list on demand.
import { getLocale } from "../i18n/store";

export interface ChangelogEntry {
  version: string;
  date: string; // YYYY-MM-DD
  es: string[];
  en: string[];
}

export const changelog: ChangelogEntry[] = [
  {
    version: "2.20.0",
    date: "2026-07-02",
    es: [
      "Metas con fecha límite: pones para cuándo la quieres y te dice cuánto apartar por periodo; te avisa si vas atrasado o si venció.",
      "Tipos de meta: «comprar algo» (al completarla se registra el gasto) o «juntar un fondo» (que puedes pasar a su propia cartera, con estilo).",
      "Apartar a una meta ya no usa montos negativos: ahora hay botones claros de Apartar y Liberar, con aporte sugerido.",
      "Toda caja de dinero muestra el signo $ y el formato con comas y centavos mientras escribes.",
      "Apartados de cartera: una cartera puede tener bolsillos (p. ej. BBVA con un apartado «Viaje a Japón»), que se muestran anidados y se despliegan al tocarlos.",
      "Transacciones: filtro por tipo con botones y por categoría según el tipo; el filtro se recuerda. Los movimientos de apartados aparecen en el historial.",
      "Calendario mejorado: año editable con flechas, ya no se recorta dentro de las ventanas.",
    ],
    en: [
      "Goals with a deadline: set when you want it and it tells you how much to set aside each period; it flags if you're behind or overdue.",
      "Goal types: \"buy something\" (completing it books the expense) or \"build a fund\" (which you can turn into its own wallet, with style).",
      "Adding to a goal no longer uses negative amounts: clear Reserve and Release buttons now, with a suggested contribution.",
      "Every money field shows the $ sign and comma/cents formatting as you type.",
      "Wallet pockets: a wallet can hold pockets (e.g. BBVA with a \"Japan trip\" pocket), shown nested and expandable on tap.",
      "Transactions: filter by type with buttons and by category per type; the filter is remembered. Pocket moves show up in the history.",
      "Better calendar: editable year with arrows, no longer clipped inside dialogs.",
    ],
  },
  {
    version: "2.19.3",
    date: "2026-06-28",
    es: [
      "Corregido: al cambiar el tamaño de cualquier contenedor del tablero (Metas de ahorro, etc.), el nuevo tamaño ahora se guarda y se mantiene al recargar.",
    ],
    en: [
      "Fixed: resizing any dashboard card (Savings goals, etc.) now saves the new size and keeps it after reload.",
    ],
  },
  {
    version: "2.19.1",
    date: "2026-06-28",
    es: [
      "Cambiar contraseña ahora tiene su propia página dedicada, más clara y enfocada.",
      "Botón para volver a Ajustes en las páginas de Apariencia, Categorías y Cambiar contraseña.",
    ],
    en: [
      "Change password now has its own dedicated page, cleaner and more focused.",
      "Back-to-Settings button on the Appearance, Categories and Change password pages.",
    ],
  },
  {
    version: "2.19.0",
    date: "2026-06-27",
    es: [
      "Simulador de inversiones: mira cuánto crecería tu dinero aportando cada mes durante el tiempo que quieras.",
      "Proyección mejorada en cada inversión: ya no sale plana; ves cómo crece a futuro, con zoom para alargar o acortar los años (también a mano).",
      "Simula aportaciones directo en la gráfica de tu inversión («si le meto X al mes…») y mira el resultado al instante.",
      "Metas de inversión: calcula cuánto aportar al mes para llegar a una cantidad.",
      "Comparador: Nu vs CETES vs BONDDIA lado a lado con tasas reales.",
      "Resumen de portafolio con tu retorno anual real (TIR) y distribución por inversión.",
      "Acceso a Metas, Presupuestos y Suscripciones desde el móvil.",
      "Novedades: este mismo aviso de cambios. Puedes apagarlo en Ajustes.",
    ],
    en: [
      "Investment simulator: see how much your money would grow contributing every month for as long as you like.",
      "Better projection on each investment: no longer flat; see it grow into the future, with zoom to stretch or shrink the years (manual too).",
      "Simulate contributions right on your investment's chart (\"if I add X per month…\") and see the result instantly.",
      "Investment goals: work out how much to contribute monthly to reach an amount.",
      "Comparator: Nu vs CETES vs BONDDIA side by side with real rates.",
      "Portfolio summary with your real annual return (XIRR) and breakdown by investment.",
      "Access Goals, Budgets and Subscriptions from mobile.",
      "What's new: this very changelog popup. You can turn it off in Settings.",
    ],
  },
  {
    version: "2.18.0",
    date: "2026-06-26",
    es: [
      "El resumen de suscripciones ahora cuenta solo lo que de verdad pagaste, no el calendario estimado.",
    ],
    en: [
      "The subscriptions summary now counts only what you actually paid, not the estimated calendar.",
    ],
  },
  {
    version: "2.17.0",
    date: "2026-06-25",
    es: [
      "Economía histórica, apartados y selector de periodo en el resumen.",
    ],
    en: [
      "Historical overview, earmarks and a period selector on the dashboard.",
    ],
  },
];

const LAST_SEEN_KEY = "finanzas.changelog.lastSeen";
const ENABLED_KEY = "finanzas.changelog.enabled";

/** Whether the auto popup on update is enabled (default true). */
export function changelogEnabled(): boolean {
  return localStorage.getItem(ENABLED_KEY) !== "false";
}
export function setChangelogEnabled(on: boolean): void {
  localStorage.setItem(ENABLED_KEY, on ? "true" : "false");
}

export function lastSeenVersion(): string | null {
  return localStorage.getItem(LAST_SEEN_KEY);
}
export function markChangelogSeen(version: string): void {
  localStorage.setItem(LAST_SEEN_KEY, version);
}

/** Semver-ish compare: > 0 when a is newer than b. */
export function compareVersions(a: string, b: string): number {
  const pa = a.split(".").map(Number);
  const pb = b.split(".").map(Number);
  for (let i = 0; i < 3; i++) {
    const d = (pa[i] || 0) - (pb[i] || 0);
    if (d !== 0) return d;
  }
  return 0;
}

/** Entries to show automatically: those newer than what the user last saw, but
 *  no newer than the running build (so unreleased notes never leak). On a fresh
 *  install (no record) we show just the current version's entry. */
export function unseenEntries(current: string): ChangelogEntry[] {
  const since = lastSeenVersion();
  if (since === null) {
    return changelog.filter((e) => e.version === current);
  }
  return changelog.filter(
    (e) => compareVersions(e.version, since) > 0 && compareVersions(e.version, current) <= 0,
  );
}

/** Full list to show in Settings, capped at the running build. */
export function visibleEntries(current: string): ChangelogEntry[] {
  return changelog.filter((e) => compareVersions(e.version, current) <= 0);
}

export function localizedChanges(e: ChangelogEntry): string[] {
  return getLocale() === "en" ? e.en : e.es;
}
