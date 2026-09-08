// Screenshots of the web app at the test phone's exact size, for comparing it
// against the APK side by side. See docs/ANDROID_PARITY_SWEEP.md.
//
//   node scripts/parity-shots.mjs                 # every screen
//   node scripts/parity-shots.mjs wallets goals   # only these
//
// Env: BASE (default http://localhost:8787), OUT (default ./parity-shots),
// EMAIL, PASSWORD, LOCALE (default en-US), SCHEME ("light" to shoot the light
// theme; dark otherwise), PERIOD ("YYYY-MM" pins the
// dashboard to that month — the widgets that need movement do not render at
// all in an empty one).
//
// One login for the whole run on purpose: the worker rate-limits sign-ins, and
// logging in per screen trips it half way through.
import { chromium } from "playwright";
import { mkdir } from "node:fs/promises";

const BASE = process.env.BASE ?? "http://localhost:8787";
const OUT = process.env.OUT ?? "parity-shots";
const EMAIL = process.env.EMAIL ?? "alan@test.mx";
const PASSWORD = process.env.PASSWORD ?? "test1234";

// The S25U reports 720×1560 at density 280, which is this viewport at this
// scale. Anything else and the two images cannot be laid on top of each other.
const VIEWPORT = { width: 411, height: 891 };
const SCALE = 1.75;

/**
 * `steps` is how many viewport-heights to walk down before stopping. `open`
 * clicks things before shooting, which is how a modal or a detail page gets on
 * screen: a regex matches a button by its accessible name, `{ css }` matches a
 * link (the rows that open a detail page are `<Link>`s, not buttons).
 */
const SCREENS = [
  { name: "home", route: "/#/", steps: 4 },
  { name: "wallets", route: "/#/carteras", steps: 3 },
  { name: "wallet-form", route: "/#/carteras", open: [/new wallet|nueva cartera/i], steps: 3 },
  { name: "wallet-detail", route: "/#/carteras", open: [{ css: "a[href*='/carteras/']" }], steps: 4 },
  // A credit card: the detail grows the statement panel and the MSI plans.
  {
    name: "wallet-detail-card",
    route: "/#/carteras",
    open: [{ css: "a[href*='/carteras/']:has-text('Tarjeta Test A')" }],
    steps: 5,
  },
  { name: "tx", route: "/#/transacciones", steps: 3 },
  { name: "tx-form", route: "/#/transacciones", open: [/new transaction|nuevo movimiento/i], steps: 3 },
  {
    name: "tx-form-expense",
    route: "/#/transacciones",
    open: [/new transaction|nuevo movimiento/i, /^expense$|^gasto$/i],
    steps: 3,
  },
  {
    name: "tx-form-transfer",
    route: "/#/transacciones",
    open: [/new transaction|nuevo movimiento/i, /^transfer$|^transferencia$/i],
    steps: 3,
  },
  // An expense on a credit card: the months-without-interest box appears.
  {
    name: "tx-form-msi",
    route: "/#/transacciones",
    open: [
      /new transaction|nuevo movimiento/i,
      /^expense$|^gasto$/i,
      { css: "select", option: "Tarjeta Test A (MXN)" },
      { css: "input[type=checkbox]" },
    ],
    steps: 3,
  },
  // The pencil on the first row. Which row it is does not matter for layout,
  // but the first one in the test account is a plain expense.
  { name: "tx-edit", route: "/#/transacciones", open: [{ css: "button[aria-label='Edit'],button[aria-label='Editar']" }], steps: 2 },
  { name: "inv", route: "/#/inversiones", steps: 3 },
  { name: "inv-form", route: "/#/inversiones", open: [/new investment|nueva inversión/i], steps: 3 },
  // The form behind one catalogue entry (each kind asks for its own fields).
  {
    name: "inv-form-cetes",
    route: "/#/inversiones",
    open: [/new investment|nueva inversión/i, { css: "button:has-text('CETES 91')" }],
    steps: 3,
  },
  {
    name: "inv-form-nu",
    route: "/#/inversiones",
    open: [/new investment|nueva inversión/i, { css: "button:has-text('Nu Box')" }],
    steps: 3,
  },
  {
    name: "inv-detail",
    route: "/#/inversiones",
    // Not just any /inversiones/ link — the simulator lives under that path too.
    open: [{ css: "a[href*='/inversiones/']:not([href*='simulador'])" }],
    steps: 4,
  },
  { name: "simulator", route: "/#/inversiones/simulador", steps: 3 },
  { name: "goals", route: "/#/metas", steps: 3 },
  { name: "goal-form", route: "/#/metas", open: [/new goal|nueva meta/i], steps: 3 },
  // Reserving into a goal, and the breakdown a category row opens on the
  // dashboard (needs PERIOD set to a month that has movement).
  { name: "goal-contribute", route: "/#/metas", open: [/^add$|^aportar$/i], steps: 2 },
  {
    name: "category-detail",
    route: "/#/",
    // The first row of the spending-by-category legend, whatever it is called
    // in the account being shot.
    open: [{ css: "ul li button:has(span.rounded-full)" }],
    steps: 2,
  },
  { name: "budgets", route: "/#/presupuestos", steps: 2 },
  { name: "budget-form", route: "/#/presupuestos", open: [/new limit|nuevo límite/i], steps: 2 },
  { name: "subs", route: "/#/suscripciones", steps: 3 },
  { name: "sub-form", route: "/#/suscripciones", open: [/new subscription|nueva suscripción/i], steps: 3 },
  { name: "cats", route: "/#/categorias", steps: 3 },
  { name: "settings", route: "/#/ajustes", steps: 4 },
  { name: "appearance", route: "/#/apariencia", steps: 4 },
  { name: "password", route: "/#/ajustes/contrasena", steps: 1 },
  // Not a route on the web: a modal opened from Settings.
  {
    name: "whats-new",
    route: "/#/ajustes",
    open: [/see the changes|ver los cambios|what's new|novedades/i],
    steps: 3,
  },
  // Signed out: shot before the run logs in.
  { name: "login", route: "/#/", steps: 1, anon: true },
  {
    name: "signup",
    route: "/#/",
    open: [/no account\? sign up|no tienes cuenta\? regístrate/i],
    steps: 2,
    anon: true,
  },
];

const wanted = process.argv.slice(2);
const screens = wanted.length
  ? SCREENS.filter((s) => wanted.includes(s.name))
  : SCREENS;

if (screens.length === 0) {
  console.error("no screens matched; known:", SCREENS.map((s) => s.name).join(" "));
  process.exit(1);
}

await mkdir(OUT, { recursive: true });

const browser = await chromium.launch();
const ctx = await browser.newContext({
  viewport: VIEWPORT,
  deviceScaleFactor: SCALE,
  isMobile: true,
  hasTouch: true,
  locale: process.env.LOCALE ?? "en-US",
  // The phone is in dark mode and the account theme is "auto"; without this the
  // browser renders light and the two are not comparable. `SCHEME=light` (with
  // `cmd uimode night no` on the phone) shoots the other half of the sweep.
  colorScheme: process.env.SCHEME === "light" ? "light" : "dark",
});
// The dashboard period lives in localStorage, so it is set before the app
// boots rather than driven through the picker.
const PERIOD = process.env.PERIOD;
if (PERIOD) {
  const [year, month] = PERIOD.split("-").map(Number);
  await ctx.addInitScript(
    ([key, value]) => localStorage.setItem(key, value),
    ["finanzas.dashboard.period", JSON.stringify({ kind: "month", year, month })],
  );
}

const page = await ctx.newPage();

await page.goto(`${BASE}/`, { waitUntil: "networkidle" });
await page.waitForSelector('input[type="email"]', { timeout: 30000 });

// Signed-out screens have to be shot before the session exists.
for (const screen of screens.filter((s) => s.anon)) {
  try {
    await shoot(screen);
    console.log(`${screen.name}  ok`);
  } catch (err) {
    console.log(`${screen.name}  FAILED  ${err.message.split("\n")[0]}`);
  }
}
await page.goto(`${BASE}/`, { waitUntil: "networkidle" });
await page.waitForSelector('input[type="email"]', { timeout: 30000 });
await page.fill('input[type="email"]', EMAIL);
await page.fill('input[type="password"]', PASSWORD);
await Promise.all([
  page.waitForURL((u) => !u.hash.includes("login"), { timeout: 20000 }),
  page.locator('button[type="submit"]').first().click(),
]);
await page.waitForTimeout(2500);
await dismissWhatsNew();

for (const screen of screens.filter((s) => !s.anon)) {
  try {
    await shoot(screen);
    console.log(`${screen.name}  ok`);
  } catch (err) {
    console.log(`${screen.name}  FAILED  ${err.message.split("\n")[0]}`);
  }
}

await browser.close();

async function dismissWhatsNew() {
  // The changelog modal pops after a version bump and covers everything.
  const close = page.getByRole("button", { name: /close|cerrar/i }).first();
  if (await close.count()) {
    await close.click().catch(() => {});
    await page.waitForTimeout(800);
  }
}

async function shoot({ name, route, steps = 1, open = [] }) {
  await page.goto(BASE + route, { waitUntil: "networkidle" });
  await page.waitForTimeout(2200);
  await dismissWhatsNew();

  for (const target of open) {
    // Once a modal is up, look inside it: the page underneath usually has a
    // button by the same name (the "Expense" filter tab vs the "Expense" tab
    // of the form), and clicking the covered one just times out.
    const overlay = page.locator("div.fixed.inset-0.z-50").first();
    const root = (await overlay.count()) ? overlay : page;
    const locator = target.css
      ? root.locator(target.css).first()
      : root.getByRole("button", { name: target }).first();
    // `{ css, option }` picks a value in a <select> instead of clicking it —
    // some states only exist once a particular wallet is chosen (a credit card
    // is what makes the MSI box appear).
    if (target.option) await locator.selectOption({ label: target.option });
    else await locator.click();
    // Some of these fetch on open — the investment form waits on Banxico — so
    // settle on the network, or the shot is of a spinner. The first pause is
    // not padding: `networkidle` resolves instantly if asked before the
    // request has even left.
    await page.waitForTimeout(700);
    await page.waitForLoadState("networkidle").catch(() => {});
    await page.waitForTimeout(900);
  }

  for (let i = 0; i < steps; i++) {
    if (i > 0) {
      const moved = await scrollDown();
      if (!moved) break; // already at the bottom; no point shooting it twice
    }
    await page.screenshot({ path: `${OUT}/web-${name}-${i}.png` });
  }
}

/**
 * The app scrolls an inner container, so `fullPage` only ever catches the first
 * screen. Walk whichever element is actually scrollable — the open modal when
 * there is one. Returns false when it did not move.
 */
async function scrollDown() {
  return page.evaluate(() => {
    const scrollable = [...document.querySelectorAll("*")].filter(
      (n) =>
        n.scrollHeight > n.clientHeight + 40 &&
        getComputedStyle(n).overflowY !== "visible",
    );
    const el = scrollable[scrollable.length - 1] ?? document.scrollingElement;
    const before = el.scrollTop;
    el.scrollBy(0, window.innerHeight - 120);
    return el.scrollTop !== before;
  });
}
