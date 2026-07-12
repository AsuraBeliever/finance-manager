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
    version: "2.26.0",
    date: "2026-07-12",
    es: [
      "En el resumen del inicio, cuando eliges un periodo sin movimientos ya no aparecen tarjetas vacías con «sin actividad»: solo se muestra lo que sí tiene datos (y el patrimonio en ceros).",
      "El patrimonio de fechas anteriores a que existiera una cartera ya no incluye su saldo inicial: los meses previos a abrirla se ven en ceros, como corresponde.",
      "Los presupuestos ya no aparecen en periodos anteriores a haberlos creado.",
    ],
    en: [
      "On the home overview, choosing a period with no activity no longer shows empty “no activity” cards: only widgets with data appear (with net worth at zero).",
      "Net worth for dates before a wallet existed no longer includes its opening balance: months prior to opening it now show zero, as they should.",
      "Budgets no longer show up in periods before you created them.",
    ],
  },
  {
    version: "2.25.0",
    date: "2026-07-12",
    es: [
      "En el resumen del inicio, toca una categoría de «Gasto por categoría» o «Ingreso por categoría» para ver el desglose de todas las transacciones que la componen en el periodo elegido.",
      "Ya puedes mostrar u ocultar la contraseña al iniciar sesión o registrarte, con el ícono de ojo.",
    ],
    en: [
      "On the home overview, tap a category in “Expenses by category” or “Income by category” to see the breakdown of every transaction behind it for the selected period.",
      "You can now show or hide your password when signing in or registering, using the eye icon.",
    ],
  },
  {
    version: "2.24.1",
    date: "2026-07-08",
    es: [
      "Las carteras con rendimiento ahora calculan los intereses igual que tu banco (redondeando cada día), así el abono coincide al centavo y ya no tienes que corregirlo a mano.",
    ],
    en: [
      "Yield-bearing wallets now calculate interest the same way your bank does (rounding each day), so the credit matches to the cent and you no longer have to fix it by hand.",
    ],
  },
  {
    version: "2.24.0",
    date: "2026-07-08",
    es: [
      "Tus metas y apartados ahora aparecen dentro de la cartera a la que pertenecen: ves su avance y puedes aportarles sin salir de la cartera.",
    ],
    en: [
      "Your goals and set-asides now appear inside the wallet they belong to: see their progress and contribute without leaving the wallet.",
    ],
  },
  {
    version: "2.23.0",
    date: "2026-07-08",
    es: [
      "Cada movimiento ahora guarda su hora, no solo la fecha: se registra al momento y puedes editarla escribiéndola a mano (sin menús).",
      "Elige el formato de hora en Ajustes: 12 h (a. m./p. m.) o 24 h.",
      "Define tu zona horaria en Ajustes para que las horas se muestren correctas.",
      "Las transferencias entre carteras ya se pueden editar.",
      "Tus movimientos anteriores ahora muestran la hora en que los registraste.",
    ],
    en: [
      "Every movement now records its time, not just the date: captured as you enter it, and editable by typing it in (no dropdowns).",
      "Pick your time format in Settings: 12 h (a.m./p.m.) or 24 h.",
      "Set your timezone in Settings so times display correctly.",
      "Transfers between wallets can now be edited.",
      "Your past movements now show the time you recorded them.",
    ],
  },
  {
    version: "2.22.2",
    date: "2026-07-03",
    es: [
      "El plan de una meta empieza cuando le pones la fecha límite: ya no aparece «Atrasada» de la nada al ponerle plazo a una meta que ya existía.",
      "La cuota del periodo se mantiene fija: si te tocan $2,400 este mes y aportas $2,000, ahora dice «llevas $2,000 de $2,400: te faltan $400» en vez de rehacer el plan.",
      "Al cubrir la cuota del periodo te lo confirma: «Este mes ya está cubierto».",
      "El sugerido del botón Aportar es lo que falta del periodo, no la cuota completa.",
      "Ya no puedes liberar más dinero del que tiene apartado la meta: sale un aviso claro en lugar de vaciarla en silencio.",
      "El formulario Aportar/Liberar abre limpio cada vez; ya no se queda pegado en «Liberar».",
    ],
    en: [
      "A goal's plan starts when you set its deadline: no more phantom \"Behind\" the moment you add a date to an existing goal.",
      "The period quota stays fixed: if this month asks for $2,400 and you put in $2,000, it now says \"you've put in $2,000 of $2,400: $400 to go\" instead of re-spreading the plan.",
      "Covering the period's quota gets confirmed: \"This month is covered\".",
      "The Contribute button's suggestion is what's left for the period, not the full quota.",
      "You can no longer release more money than the goal holds: a clear error shows instead of silently draining it.",
      "The Contribute/Release form opens fresh every time; it no longer sticks on \"Release\".",
    ],
  },
  {
    version: "2.22.1",
    date: "2026-07-03",
    es: [
      "Los movimientos de apartados (Apartado/Liberado) en el historial ahora tienen lápiz y bote: edita su monto o fecha, o bórralos, y lo apartado en la meta se ajusta solo.",
    ],
    en: [
      "Pocket moves (Reserved/Released) in the history now have pencil and trash: edit their amount or date, or delete them, and the goal's reserved amount adjusts itself.",
    ],
  },
  {
    version: "2.22.0",
    date: "2026-07-02",
    es: [
      "Compra a MSI desde el formulario normal de gasto: si la cartera elegida es una tarjeta con corte, marca «Compra a meses sin intereses» y listo.",
      "Cada compra a MSI lleva su categoría de gasto, así las mensualidades cuentan en tus presupuestos y análisis.",
      "Al capturar un MSI ves en vivo «≈ $X al mes · primer cargo el día D», y al guardar una confirmación con el primer y último cargo.",
      "Pagar la tarjeta con más contexto: al transferirle ves cuánto falta del corte y su fecha límite, y al guardar te dice si quedó liquidado o cuánto falta.",
      "Al dar de alta una tarjeta puedes registrar la deuda que ya traías («Deuda actual»).",
      "Configurar una tarjeta es más directo: elegir la categoría «Tarjeta de crédito» muestra sus campos, sin interruptor aparte.",
    ],
    en: [
      "MSI purchases from the regular expense form: if the chosen wallet is a card with a cut-off, tick \"Interest-free installments\" and you're done.",
      "Each MSI purchase carries its expense category, so the monthly charges count in your budgets and analytics.",
      "While entering an MSI you see live \"≈ $X per month · first charge on day D\", and a confirmation with the first and last charge on save.",
      "Paying the card with more context: when transferring to it you see what's left of the statement and its due date, and on save it tells you whether it's settled or how much is missing.",
      "When creating a card you can register the debt you already carried (\"Current debt\").",
      "Setting up a card is more direct: picking the \"Credit card\" category reveals its fields, no separate toggle.",
    ],
  },
  {
    version: "2.21.0",
    date: "2026-07-02",
    es: [
      "Tarjetas de crédito de verdad: marca una cartera como tarjeta y registra su día de corte, días para pagar, límite de crédito y anualidad.",
      "El detalle de la tarjeta muestra tu deuda, el saldo al corte y cuánto pagar antes de la fecha límite para no generar intereses.",
      "Barra de uso del crédito (verde/ámbar/rojo) y crédito disponible, contando también lo comprometido a meses.",
      "Compras a meses sin intereses (MSI): regístralas una vez y cada mensualidad se carga sola en tu historial en cada corte.",
      "Pagar la tarjeta es tan simple como siempre: una transferencia desde tu cartera de débito.",
    ],
    en: [
      "Real credit cards: mark a wallet as a card and track its cut-off day, days to pay, credit limit and annual fee.",
      "The card's detail shows your debt, the statement balance and how much to pay before the deadline to avoid interest.",
      "Credit usage bar (green/amber/red) and available credit, also counting what's committed to installments.",
      "Interest-free installment purchases (MSI): register them once and each monthly charge posts itself on every cut-off.",
      "Paying the card stays as simple as ever: a transfer from your debit wallet.",
    ],
  },
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
