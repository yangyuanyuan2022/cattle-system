import fs from "node:fs/promises";
import { chromium } from "playwright";

const web = process.env.WEB_BASE_URL || "http://127.0.0.1:5173";
await fs.mkdir("artifacts", { recursive: true });
const browser = await chromium.launch({ channel: "msedge", headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
const page = await context.newPage();
const failures = [];
page.on("response", (response) => { if (response.status() >= 400 && !response.url().endsWith("/favicon.ico")) failures.push(`${response.status()} ${response.url()}`); });
page.on("pageerror", (error) => failures.push(error.message));
await page.goto(`${web}/login`, { waitUntil: "networkidle" });
await page.locator("input").nth(0).fill("admin");
await page.locator("input").nth(1).fill("123456");
await page.locator("button").filter({ hasText: /登录|鐧诲綍/ }).click();
await page.waitForURL(`${web}/`);

await page.goto(`${web}/cattle`, { waitUntil: "networkidle" });
await page.screenshot({ path: "artifacts/cattle-filters-final.png", fullPage: true });
await page.goto(`${web}/growth`, { waitUntil: "networkidle" });
await page.locator(".growth-mode .el-segmented__item").nth(1).click();
await page.waitForTimeout(600);
await page.screenshot({ path: "artifacts/growth-herd-final.png", fullPage: true });
await page.goto(`${web}/vaccinations`, { waitUntil: "networkidle" });
await page.screenshot({ path: "artifacts/vaccination-trace-final.png", fullPage: true });
await page.goto(`${web}/health`, { waitUntil: "networkidle" });
const detailButton = page.locator(".el-table__body button").filter({ hasText: /详情|璇︽儏/ }).first();
if (await detailButton.count()) { await detailButton.click(); await page.waitForTimeout(400); }
await page.screenshot({ path: "artifacts/health-detail-final.png", fullPage: true });

await page.setViewportSize({ width: 390, height: 844 });
await page.goto(`${web}/cattle`, { waitUntil: "networkidle" });
const overflow = await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1);
await page.screenshot({ path: "artifacts/cattle-filters-mobile-final.png", fullPage: true });
if (overflow) failures.push("mobile cattle page has horizontal document overflow");
await browser.close();
if (failures.length) throw new Error(failures.join("\n"));
console.log("Final screenshots passed: cattle filters, herd trend, vaccination trace, health detail, mobile cattle");
