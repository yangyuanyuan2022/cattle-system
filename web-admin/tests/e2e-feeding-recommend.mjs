import { chromium } from "playwright";
import { mkdir } from "node:fs/promises";
import { fileURLToPath } from "node:url";

const baseURL = process.env.WEB_BASE_URL || "http://127.0.0.1:5173";
const output = new URL("../artifacts/", import.meta.url);
await mkdir(output, { recursive: true });
const browser = await chromium.launch({ channel: "msedge", headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 1200 } });
const errors = [];
page.on("pageerror", (error) => errors.push(error.message));
page.on("console", (message) => {
  if (message.type() === "error" && !message.text().includes("Failed to load resource")) errors.push(message.text());
});
page.on("response", (response) => {
  if (response.status() >= 400 && !response.url().endsWith("/favicon.ico"))
    errors.push(`${response.status()} ${response.url()}`);
});

await page.goto(`${baseURL}/login`, { waitUntil: "networkidle" });
await page.locator("input").nth(0).fill("admin");
await page.locator("input").nth(1).fill("123456");
await page.locator("button").filter({ hasText: "登录" }).click();
await page.waitForURL(`${baseURL}/`);
await page.goto(`${baseURL}/feeding`, { waitUntil: "networkidle" });
await page.keyboard.press("Escape");
await page.waitForTimeout(500);
await page.getByText("共 71 种", { exact: true }).waitFor();
await page.screenshot({ path: fileURLToPath(new URL("feeding-ingredients-final.png", output)), fullPage: true });
await page.getByText("日粮配方", { exact: true }).click();
await page.getByRole("button", { name: "智能生成" }).click();
const dialog = page.getByRole("dialog", { name: "智能生成育肥配方" });
await dialog.waitFor();
const numericInputs = dialog.locator(".el-input-number input");
await numericInputs.nth(0).fill("400");
await numericInputs.nth(1).fill("600");
await numericInputs.nth(2).fill("180");
await dialog.getByRole("button", { name: "点击选择原料 展开" }).click();
const ingredientPicker = page.locator(".ingredient-picker:visible");
await ingredientPicker.waitFor();
for (const name of ["全株玉米青贮", "玉米", "豆粕"]) {
  await ingredientPicker.locator(".el-checkbox__label").getByText(name, { exact: true }).click();
}
await dialog.locator(".el-dialog__header").click();
await ingredientPicker.waitFor({ state: "hidden" }).catch(() => {});
const responsePromise = page.waitForResponse((response) => response.url().includes("/ration-formulas/recommend"), { timeout: 1000 }).catch(() => null);
await dialog.getByRole("button", { name: "生成建议" }).click();
const recommendResponse = await responsePromise;
if (!recommendResponse) {
  const message = await page.locator(".el-message:visible").allTextContents();
  const buttonClass = await dialog.getByRole("button", { name: "生成建议" }).getAttribute("class");
  await page.screenshot({ path: fileURLToPath(new URL("feeding-recommendation-debug.png", output)), fullPage: true });
  const resources = await page.evaluate(() => performance.getEntriesByType("resource").map((entry) => entry.name).filter((name) => name.includes("feeding")));
  throw new Error(`Recommendation request was not sent. Button: ${buttonClass}. Messages: ${message.join(" | ")}. Resources: ${resources.join(" | ")}. Errors: ${errors.join(" | ")}`);
}
if (!recommendResponse.ok()) throw new Error(`Recommendation API failed: ${recommendResponse.status()} ${await recommendResponse.text()}`);
await dialog.locator(".recommend-metrics").waitFor();
await dialog.getByText("采用并继续编辑", { exact: true }).waitFor();
await page.screenshot({ path: fileURLToPath(new URL("feeding-recommendation-final.png", output)), fullPage: true });
await page.goto(`${baseURL}/feeding?tab=micronutrients`, { waitUntil: "networkidle" });
await page.keyboard.press("Escape");
await page.locator(".el-overlay").waitFor({ state: "hidden" }).catch(() => {});
await page.getByRole("button", { name: "计算需要量" }).click();
await page.getByRole("cell", { name: "钙", exact: true }).waitFor();
await page.getByText("2580", { exact: false }).first().waitFor();
await page.screenshot({ path: fileURLToPath(new URL("feeding-micronutrients-final.png", output)), fullPage: true });
await page.goto(`${baseURL}/feeding?tab=breeding`, { waitUntil: "networkidle" });
await page.keyboard.press("Escape");
await page.getByRole("button", { name: "计算需要量" }).click();
await page.getByRole("cell", { name: "干物质采食量", exact: true }).waitFor();
await page.getByText("7.5", { exact: false }).first().waitFor();
await page.getByText("54.67%", { exact: true }).waitFor();
await page.screenshot({ path: fileURLToPath(new URL("feeding-breeding-nutrition-final.png", output)), fullPage: true });
if (errors.length) throw new Error(errors.join("\n"));
await browser.close();
console.log("Feeding recommendation E2E passed: inputs, ingredient selection, calculation and editable handoff");
