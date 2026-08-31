// Captures the web app at the test phone's exact screen (720x1560 @1.75), so
// its screenshots line up pixel-for-pixel with `adb exec-out screencap`.
// Usage: node shot-web.mjs <route> <name> [scrollPx]
import { chromium } from "playwright";

const BASE = "http://localhost:8787";
const OUT = process.env.SHOT_OUT ?? "/tmp/shots";
const jobs = JSON.parse(process.env.SHOT_JOBS ?? "[]"); // [[route, name, scroll?], ...]

const browser = await chromium.launch();
const ctx = await browser.newContext({
  viewport: { width: 411, height: 891 },
  deviceScaleFactor: 1.75,
  isMobile: true,
  hasTouch: true,
  colorScheme: "dark",
  locale: "en-US",
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
await page.waitForTimeout(400);

for (const [route, name, scroll] of jobs) {
  await page.goto(`${BASE}/#${route}`, { waitUntil: "networkidle" });
  await page.waitForTimeout(2600);
  await page.keyboard.press("Escape").catch(() => {});
  await page.waitForTimeout(400);
  if (scroll) {
    await page.mouse.wheel(0, scroll);
    await page.waitForTimeout(900);
  }
  await page.screenshot({ path: `${OUT}/web-${name}.png` });
  console.log("shot", name);
}
await browser.close();
