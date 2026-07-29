import { chromium } from "playwright";
import { mkdir } from "node:fs/promises";
import { fileURLToPath } from "node:url";

const baseURL = process.env.WEB_BASE_URL || "http://127.0.0.1:5173";
const output = new URL("../artifacts/", import.meta.url);
await mkdir(output, { recursive: true });
const screenshot = (name) => fileURLToPath(new URL(name, output));
const browser = await chromium.launch({ channel: "msedge", headless: true });
const context = await browser.newContext({
  viewport: { width: 1440, height: 960 },
});
const page = await context.newPage();
const errors = [];
page.on("console", (message) => {
  if (
    message.type() === "error" &&
    !message.text().includes("Failed to load resource")
  )
    errors.push(`console: ${message.text()}`);
});
page.on("pageerror", (error) => errors.push(`page: ${error.message}`));
page.on("response", (response) => {
  if (response.status() >= 400 && !response.url().endsWith("/favicon.ico"))
    errors.push(`http ${response.status()}: ${response.url()}`);
});

await page.goto(`${baseURL}/login`, { waitUntil: "networkidle" });
await page.getByLabel("账号").fill("admin");
await page.getByLabel("密码").fill("123456");
await page.getByRole("button", { name: "登录" }).click();
await page.waitForURL(`${baseURL}/`);
await page.getByRole("heading", { name: "工作台" }).waitFor();
await page.screenshot({
  path: screenshot("dashboard-desktop.png"),
  fullPage: true,
});

const routes = [
  ["/", "工作台"],
  ["/cattle", "牛只档案"],
  ["/cattle/import", "批量导入"],
  ["/movements", "转群与离场记录"],
  ["/locations", "栏舍与牛群"],
  ["/tasks", "任务中心"],
  ["/breeding", "母牛繁育档案"],
  ["/health", "健康病例"],
  ["/vaccinations", "防疫计划"],
  ["/growth", "单牛生长趋势"],
  ["/feeding", "配料管理"],
  ["/reports", "报表中心"],
  ["/audit", "审计中心"],
  ["/settings", "系统配置"],
  ["/settings/users", "用户权限"],
];
for (const [path, heading] of routes) {
  await page.goto(`${baseURL}${path}`, { waitUntil: "networkidle" });
  await page.getByRole("heading", { name: heading, exact: true }).waitFor();
  const overflow = await page.evaluate(
    () =>
      document.documentElement.scrollWidth -
      document.documentElement.clientWidth,
  );
  if (overflow > 1) errors.push(`overflow ${overflow}px: ${path}`);
}
await page.goto(`${baseURL}/settings`, { waitUntil: "networkidle" });
await page.screenshot({
  path: screenshot("settings-desktop.png"),
  fullPage: true,
});

await page.goto(`${baseURL}/cattle`, { waitUntil: "networkidle" });
const firstCattle = page.locator(".desktop-cattle-table tbody tr").first();
if (await firstCattle.count()) {
  await firstCattle.click();
  await page.getByText("牛只详情", { exact: true }).waitFor();
  for (const tab of [
    "概览",
    "时间轴",
    "繁育",
    "健康",
    "生长",
    "转群",
    "附件",
  ]) {
    await page.getByRole("tab", { name: tab, exact: true }).click();
  }
  await page.getByRole("button", { name: "二维码" }).click();
  await page.getByText("牛牌二维码", { exact: true }).waitFor();
  await page.screenshot({
    path: screenshot("cattle-detail-desktop.png"),
    fullPage: true,
  });
  await page.keyboard.press("Escape");
  await page.keyboard.press("Escape");
}

const mobile = await context.newPage();
await mobile.setViewportSize({ width: 390, height: 844 });
await mobile.goto(`${baseURL}/cattle`, { waitUntil: "networkidle" });
await mobile.getByRole("heading", { name: "牛只档案" }).waitFor();
const mobileOverflow = await mobile.evaluate(
  () =>
    document.documentElement.scrollWidth - document.documentElement.clientWidth,
);
if (mobileOverflow > 1)
  errors.push(`mobile overflow ${mobileOverflow}px: /cattle`);
await mobile.screenshot({
  path: screenshot("cattle-mobile.png"),
  fullPage: true,
});

async function useRole(role) {
  await page.evaluate((nextRole) => {
    const user = JSON.parse(localStorage.getItem("current_user"));
    user.roles = [nextRole];
    localStorage.setItem("current_user", JSON.stringify(user));
  }, role);
  await page.goto(`${baseURL}/`, { waitUntil: "networkidle" });
}

async function expectVisible(name, visible = true) {
  const count = await page.getByText(name, { exact: true }).count();
  if (count > 0 !== visible)
    errors.push(`${name} should be ${visible ? "visible" : "hidden"}`);
}

await useRole("VET");
await expectVisible("繁育管理");
await page.goto(`${baseURL}/breeding`, { waitUntil: "networkidle" });
if (await page.getByRole("button", { name: "记录发情" }).count())
  errors.push("VET can write breeding");
await page.goto(`${baseURL}/health`, { waitUntil: "networkidle" });
if (!(await page.getByRole("button", { name: "上报健康异常" }).count()))
  errors.push("VET cannot write health");

await useRole("BREEDER");
await expectVisible("健康诊疗");
await page.goto(`${baseURL}/health`, { waitUntil: "networkidle" });
if (await page.getByRole("button", { name: "上报健康异常" }).count())
  errors.push("BREEDER can write health");
await page.goto(`${baseURL}/breeding`, { waitUntil: "networkidle" });
if (!(await page.getByRole("button", { name: "记录发情" }).count()))
  errors.push("BREEDER cannot write breeding");

await useRole("WORKER");
for (const hidden of ["报表中心", "审计中心", "系统配置", "用户权限"])
  await expectVisible(hidden, false);
await expectVisible("健康诊疗");
await expectVisible("繁育管理");
await page.goto(`${baseURL}/health`, { waitUntil: "networkidle" });
if (!(await page.getByRole("button", { name: "上报健康异常" }).count()))
  errors.push("WORKER cannot report health abnormalities");
await page.goto(`${baseURL}/breeding`, { waitUntil: "networkidle" });
if (await page.getByRole("button", { name: "记录发情" }).count())
  errors.push("WORKER can write breeding");
await page.goto(`${baseURL}/tasks`, { waitUntil: "networkidle" });
if (await page.getByRole("button", { name: "创建任务" }).count())
  errors.push("WORKER can create tasks");
const workerRequests = [];
const captureWorkerRequest = (request) => workerRequests.push(request.url());
page.on("request", captureWorkerRequest);
await page.goto(`${baseURL}/feeding`, { waitUntil: "networkidle" });
page.off("request", captureWorkerRequest);
if (
  await page
    .getByRole("button", { name: /新增原料|新建配方|创建配料单|配料计算/ })
    .count()
)
  errors.push("WORKER can create feeding master data");
if (workerRequests.some((url) => url.includes("/feeding/ingredients")))
  errors.push("WORKER requested forbidden feeding ingredients API");
await page.goto(`${baseURL}/reports`, { waitUntil: "networkidle" });
if (new URL(page.url()).pathname !== "/")
  errors.push("WORKER can access reports route");
await browser.close();

if (errors.length) {
  console.error(errors.join("\n"));
  process.exit(1);
}
console.log(
  `E2E passed: ${routes.length} routes, desktop/mobile viewports, VET/BREEDER/WORKER permissions`,
);
