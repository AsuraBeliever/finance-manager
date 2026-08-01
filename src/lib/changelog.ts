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
    version: "2.29.0",
    date: "2026-08-01",
    es: [
      "Una transferencia ya se ve como un solo movimiento. Antes salía en dos renglones (uno con el dinero saliendo y otro con el dinero entrando), como si fueran dos cosas distintas. Ahora es una sola línea que dice de dónde a dónde fue («A → Ahorros») y el monto una vez, sin signo: el dinero no salió, se movió. Dentro de una cartera sigue apareciendo con su signo, porque ahí sí salió de esa cuenta.",
      "Al transferir, los apartados de la cartera de la que sale el dinero ahora encabezan la lista de destinos, en su propio grupo. Antes estaban revueltos entre todas las demás carteras y había que buscarlos. Si el origen es un apartado, arriba te salen su cartera y los apartados hermanos.",
      "El historial ya se ordena por la hora que muestra. Los apartados de metas y los movimientos de inversión se guardan sin hora propia, y eso los mandaba hasta abajo del día: veías un movimiento de las 5:25 debajo de uno de las 5:13. Ahora todo el día queda en orden, de lo más reciente a lo más viejo.",
    ],
    en: [
      "A transfer now reads as one movement. It used to take two rows (one for the money leaving, one for it arriving), as if they were two different things. It's now a single line naming where it went from and to (\"A → Savings\") with the amount once, unsigned: the money didn't leave, it moved. Inside a wallet it still shows its sign, because there it did leave that account.",
      "When transferring, the pockets of the wallet the money comes from now lead the destination list, in their own group. They used to be mixed in with every other wallet, so you had to hunt for them. If the source is a pocket, its wallet and sibling pockets come first instead.",
      "History now sorts by the time it shows. Goal pockets and investment moves are saved without a time of their own, and that sank them to the bottom of the day: you'd see a 5:25 movement below a 5:13 one. The whole day now reads newest to oldest.",
    ],
  },
  {
    version: "2.28.2",
    date: "2026-08-01",
    es: [
      "Al hacer una transferencia, el campo «Hacia» ya no viene con una cartera puesta de antemano. Antes traía preseleccionada la primera cartera de la lista, así que si guardabas sin abrir ese campo, el dinero se iba a una cartera que tú no elegiste. Ahora arranca vacío y el botón Guardar no se activa hasta que digas a dónde va.",
      "Los apartados ahora se leen con su cartera («NU › Ahorros») en los dos selectores de la transferencia, para que no se confundan con la cartera de junto en la lista.",
      "Un apartado que acabas de crear ya aparece de inmediato en la lista de destinos, sin tener que recargar la app.",
    ],
    en: [
      "When making a transfer, the \"To\" field no longer comes with a wallet already filled in. It used to preselect the first wallet on the list, so saving without opening that field sent the money to a wallet you never chose. It now starts empty and Save stays disabled until you say where the money goes.",
      "Pockets now read with their wallet (\"NU › Ahorros\") in both transfer pickers, so they aren't mistaken for the wallet next to them on the list.",
      "A pocket you just created now shows up in the destination list right away, with no need to reload the app.",
    ],
  },
  {
    version: "2.28.1",
    date: "2026-07-28",
    es: [
      "El valor de tu inversión de cetesdirecto (BONDDIA) ya no se queda un día atrás. La página oficial publica el precio del fondo durante la mañana, y la app lo consultaba a la 1 de la madrugada, cuando todavía estaba el del día anterior: por eso tu saldo aparecía un día de rendimiento abajo del que ves en cetesdirecto. Ahora el precio se consulta también a las 9 de la mañana y a las 2 de la tarde. Si abres la app de madrugada todavía verás el valor del día anterior, igual que en cetesdirecto antes de que publique.",
    ],
    en: [
      "Your cetesdirecto (BONDDIA) investment value no longer lags a day behind. The official page publishes the fund price during the morning, and the app was reading it at 1 AM, when it still showed the previous day's: that left your balance one day of yield below what cetesdirecto shows. The price is now also fetched at 9 AM and 2 PM. Opening the app overnight still shows the previous day's value, just like cetesdirecto before it publishes.",
    ],
  },
  {
    version: "2.28.0",
    date: "2026-07-28",
    es: [
      "Aportar o retirar en una inversión ya no cuenta como gasto ni como ingreso: ahora se registra como transferencia entre tu cartera y la inversión, que es lo que realmente pasa (el dinero solo cambia de lugar). Así, aunque muevas el mismo dinero muchas veces, tus totales de ingresos y gastos, tus presupuestos y tus gráficas dejan de inflarse.",
      "Tus aportes y retiros anteriores se reclasificaron solos a transferencia; los saldos de tus carteras no cambian.",
      "Ya puedes corregir una aportación o un retiro ya registrado (monto, fecha, cartera, o si era aporte y no retiro), tanto desde la inversión como desde el historial de transacciones. La inversión y el movimiento de la cartera se corrigen juntos, así que nunca quedan en desacuerdo.",
    ],
    en: [
      "Depositing into or withdrawing from an investment no longer counts as an expense or income: it's now recorded as a transfer between your wallet and the investment, which is what actually happens (the money just changes place). So even if you move the same money many times, your income and expense totals, budgets and charts stop being inflated.",
      "Your previous deposits and withdrawals were reclassified as transfers automatically; your wallet balances are unchanged.",
      "You can now fix a recorded deposit or withdrawal (amount, date, wallet, or deposit vs withdrawal), both from the investment and from the transactions history. The investment and the wallet move are corrected together, so they never disagree.",
    ],
  },
  {
    version: "2.27.2",
    date: "2026-07-24",
    es: [
      "Los aportes y retiros a una inversión BONDDIA por títulos ahora calculan el número de títulos igual que cetesdirecto: se toma todo el efectivo disponible (el que dejó suelto la operación anterior más el aporte) y se compran los títulos enteros que alcancen. Así el conteo de títulos coincide con cetesdirecto por sí solo, sin tener que ajustarlo a mano.",
    ],
    en: [
      "Deposits and withdrawals to a títulos-based BONDDIA investment now compute the share count the same way cetesdirecto does: it takes all available cash (whatever the previous operation left over, plus the new amount) and buys as many whole shares as it covers. So the títulos count matches cetesdirecto on its own, with no manual adjusting.",
    ],
  },
  {
    version: "2.27.1",
    date: "2026-07-24",
    es: [
      "Al aportar o retirar en una inversión BONDDIA por títulos (la de cetesdirecto), el dinero ahora se convierte a títulos al precio del día, así que además de reflejarse al instante sigue creciendo con el fondo. Antes se quedaba como efectivo que no crecía y se desviaba unos centavos cada día de tu saldo real de cetesdirecto.",
    ],
    en: [
      "When you deposit into or withdraw from a títulos-based BONDDIA investment (cetesdirecto's), the money is now converted to fund shares at that day's price, so on top of showing up immediately it keeps growing with the fund. Before, it stayed as cash that didn't grow and drifted a few cents a day from your real cetesdirecto balance.",
    ],
  },
  {
    version: "2.27.0",
    date: "2026-07-24",
    es: [
      "Al aportar o retirar en una inversión BONDDIA (la de cetesdirecto por títulos), el total ahora sube o baja al instante por el monto del movimiento, tal como en tu app de cetesdirecto. Antes el aporte salía de tu cartera pero no se reflejaba en la inversión.",
      "Los aportes y retiros que haces desde una cartera hacia una inversión ahora quedan clasificados en la categoría «Inversiones» (antes salían sin categoría en tus gastos e ingresos).",
      "La proyección «¿cuánto crecería?» ahora considera bien una aportación única (sin repetición): antes la ignoraba y no mostraba el crecimiento.",
      "En el detalle de una cartera, el número grande ahora es lo realmente disponible (saldo menos lo apartado en metas), con una línea de «Total con apartados» debajo.",
    ],
    en: [
      "Depositing into or withdrawing from a BONDDIA investment (cetesdirecto's títulos-based fund) now moves the total by the movement amount immediately, just like in your cetesdirecto app. Before, the deposit left your wallet but didn't show up in the investment.",
      "Contributions and withdrawals you make from a wallet into an investment are now filed under the “Investments” category (they used to be uncategorized in your expenses and income).",
      "The “how much would it grow?” projection now handles a one-time contribution (no repetition) correctly: it used to ignore it and show no growth.",
      "On a wallet's detail, the big number is now what's actually available (balance minus what's reserved in goals), with a “Total with pockets” line below.",
    ],
  },
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
