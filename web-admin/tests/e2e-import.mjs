import fs from "node:fs/promises";
import { chromium } from "playwright";

const web = process.env.WEB_BASE_URL || "http://127.0.0.1:5173";
const api = process.env.API_BASE_URL || "http://127.0.0.1:8080/api/v1";
const file = process.env.IMPORT_FILE;
const earTag = process.env.IMPORT_EAR_TAG;
if (!file || !earTag) throw new Error("IMPORT_FILE and IMPORT_EAR_TAG are required");
await fs.access(file);

async function apiRequest(path, { token, method = "GET", body } = {}) {
  const response = await fetch(api + path, {
    method,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(body ? { "Content-Type": "application/json", "X-Idempotency-Key": crypto.randomUUID() } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const payload = await response.json();
  if (!response.ok || payload.code !== "SUCCESS") throw new Error(`${method} ${path}: ${response.status} ${payload.message}`);
  return payload.data;
}

const admin = await apiRequest("/auth/login", { method: "POST", body: { username: "admin", password: "123456" } });
const browser = await chromium.launch({ channel: "msedge", headless: true });
const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
const failures = [];
page.on("response", (response) => { if (response.status() >= 400 && !response.url().endsWith("/favicon.ico")) failures.push(`${response.status()} ${response.url()}`); });
try {
  await page.goto(`${web}/login`, { waitUntil: "networkidle" });
  await page.locator("input").nth(0).fill("admin");
  await page.locator("input").nth(1).fill("123456");
  await page.locator("button").filter({ hasText: /登录|鐧诲綍/ }).click();
  await page.waitForURL(`${web}/`);
  await page.goto(`${web}/cattle/import`, { waitUntil: "networkidle" });
  await page.locator('input[type="file"]').setInputFiles(file);
  await page.locator(".import-actions button").nth(0).click();
  await page.locator(".import-summary").waitFor({ state: "visible" });
  const summary = await page.locator(".import-summary").innerText();
  if (!/0\s*$|错误\s*0|閿欒\s*0/m.test(summary)) throw new Error(`validation did not pass: ${summary}`);
  await page.locator(".import-actions button").nth(1).click();
  await page.locator(".el-message-box__btns button").last().click();
  await page.waitForFunction(() => document.querySelector(".import-summary")?.textContent?.includes("CONFIRMED") || document.querySelector(".import-summary")?.textContent?.includes("已导入"));
  const result = await apiRequest(`/cattle?page=1&pageSize=20&keyword=${encodeURIComponent(earTag)}`, { token: admin.accessToken });
  const cattle = result.items.find((item) => item.earTagNo === earTag);
  if (!cattle) throw new Error("imported cattle not found");
  await apiRequest(`/cattle/${cattle.cattleId}/archive`, { token: admin.accessToken, method: "POST", body: { exitType: "OTHER", exitDate: new Date().toISOString().slice(0, 10), reason: "自动化导入验收后归档", treatingRiskConfirmed: true, version: cattle.version } });
  console.log(`Import E2E passed: ${earTag} validated, imported, and archived`);
} finally {
  await browser.close();
}
if (failures.length) throw new Error(`HTTP failures: ${failures.join(", ")}`);
