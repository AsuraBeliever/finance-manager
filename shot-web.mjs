import { chromium } from "playwright";
const BASE = "http://localhost:8787";
const OUT = process.env.SHOT_OUT;
const jobs = JSON.parse(process.env.SHOT_JOBS ?? "[]");
const theme = process.env.SHOT_THEME ?? "dark";
const browser = await chromium.launch();
const ctx = await browser.newContext({
  viewport: { width: 411, height: 891 }, deviceScaleFactor: 1.75,
  isMobile: true, hasTouch: true, colorScheme: theme, locale: "en-US",
});
const page = await ctx.newPage();
await page.goto(`${BASE}/#/`, { waitUntil: "networkidle" });
if (await page.locator('input[type="password"]').first().isVisible().catch(() => false)) {
  await page.locator('input[type="email"]').first().fill("alan@test.mx");
  await page.locator('input[type="password"]').first().fill("test1234");
  await page.locator('button[type="submit"]').first().click();
  await page.waitForTimeout(3500);
}
await page.keyboard.press("Escape").catch(() => {});
for (const [route, name, opts = {}] of jobs) {
  await page.goto(`${BASE}/#${route}`, { waitUntil: "networkidle" });
  await page.waitForTimeout(opts.wait ?? 3000);
  await page.keyboard.press("Escape").catch(() => {});
  await page.waitForTimeout(500);
  if (opts.scroll) { await page.mouse.wheel(0, opts.scroll); await page.waitForTimeout(900); }
  await page.screenshot({ path: `${OUT}/web-${name}.png` });
  console.log("shot", name);
}
await browser.close();
