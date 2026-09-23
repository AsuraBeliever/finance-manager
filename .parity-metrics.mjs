// Inventory of every distinct text style the web renders, per route.
import { chromium } from "playwright";
const b = await chromium.launch();
const ctx = await b.newContext({
  viewport: { width: 448, height: 997 }, deviceScaleFactor: 3,
  isMobile: true, hasTouch: true, locale: "en-US", colorScheme: "dark",
});
await ctx.addCookies([{ name: "session", value: process.env.SESSION, domain: "localhost", path: "/", httpOnly: true, secure: false }]);
const p = await ctx.newPage();
const seen = new Map();

const collect = async (route, opens = []) => {
  await p.goto(`http://localhost:8787/#${route}`, { waitUntil: "networkidle" });
  await p.waitForTimeout(2200);
  const close = p.getByRole("button", { name: /close|cerrar/i }).first();
  if (await close.count()) { await close.click().catch(() => {}); await p.waitForTimeout(600); }
  for (const o of opens) {
    await p.getByRole("button", { name: new RegExp(o, "i") }).first().click().catch(() => {});
    await p.waitForTimeout(1200);
  }
  const rows = await p.evaluate(() => {
    const out = [];
    for (const el of document.querySelectorAll("*")) {
      const direct = [...el.childNodes].some((n) => n.nodeType === 3 && n.textContent.trim());
      if (!direct) continue;
      const c = getComputedStyle(el);
      if (c.display === "none" || c.visibility === "hidden") continue;
      out.push({
        key: [c.fontFamily.split(",")[0].replace(/"/g, ""), c.fontSize, c.fontWeight,
              c.letterSpacing, c.lineHeight].join(" | "),
        sample: el.textContent.trim().slice(0, 28),
      });
    }
    return out;
  });
  for (const r of rows) if (!seen.has(r.key)) seen.set(r.key, `${route} “${r.sample}”`);
};

await collect("/");
await collect("/carteras");
await collect("/carteras", ["new wallet"]);
await collect("/transacciones");
await collect("/transacciones", ["new transaction"]);
await collect("/inversiones");
await collect("/inversiones/simulador");
await collect("/metas");
await collect("/ajustes");
await collect("/categorias");
for (const [k, v] of [...seen].sort()) console.log(k.padEnd(56), v);
await b.close();
