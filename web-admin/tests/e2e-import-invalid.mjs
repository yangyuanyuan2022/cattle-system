import fs from "node:fs/promises";
import { chromium } from "playwright";

const web = process.env.WEB_BASE_URL || "http://127.0.0.1:5173";
const file = process.env.INVALID_IMPORT_FILE;
if (!file) throw new Error("INVALID_IMPORT_FILE is required");
await fs.access(file);
const browser = await chromium.launch({ channel: "msedge", headless: true });
const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
try {
  await page.goto(`${web}/login`, { waitUntil: "networkidle" });
  await page.locator("input").nth(0).fill("admin");
  await page.locator("input").nth(1).fill("123456");
  await page.locator("button").filter({ hasText: /登录|鐧诲綍/ }).click();
  await page.waitForURL(`${web}/`);
  await page.goto(`${web}/cattle/import`, { waitUntil: "networkidle" });
  await page.locator('input[type="file"]').setInputFiles(file);
  await page.locator(".import-actions button").nth(0).click();
  await page.locator(".table-panel").filter({ has: page.locator(".el-table") }).first().waitFor({ state: "visible" });
  if (!(await page.locator(".import-actions button").nth(1).isDisabled())) throw new Error("confirm button must be disabled for invalid import");
  const rows = await page.locator(".table-panel .el-table__body tr").count();
  if (!rows) throw new Error("invalid import did not show row errors");
  console.log(`Invalid import E2E passed: ${rows} error row(s), confirm disabled`);
} finally {
  await browser.close();
}
