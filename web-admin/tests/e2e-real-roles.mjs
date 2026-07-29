import { chromium } from "playwright";
const web = process.env.WEB_BASE_URL || "http://127.0.0.1:5173",
  api = process.env.API_BASE_URL || "http://127.0.0.1:8080/api/v1",
  password = "Test123456!",
  stamp = Date.now(),
  created = [];
async function request(path, { token, method = "GET", body } = {}) {
  const response = await fetch(api + path, {
    method,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(body
        ? {
            "Content-Type": "application/json",
            "X-Idempotency-Key": crypto.randomUUID(),
          }
        : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const payload = await response.json().catch(() => null);
  if (!response.ok || payload?.code !== "SUCCESS")
    throw new Error(
      `${method} ${path}: ${response.status} ${payload?.message || ""}`,
    );
  return payload.data;
}
const admin = await request("/auth/login", {
  method: "POST",
  body: { username: "admin", password: "123456" },
});
async function create(role) {
  const username = `e2e_${role.toLowerCase()}_${stamp}`;
  const user = await request("/users", {
    token: admin.accessToken,
    method: "POST",
    body: {
      username,
      password,
      realName: `E2E ${role}`,
      roleCodes: [role],
      dataScopes: [
        {
          scopeType: role === "WORKER" ? "SELF_ASSIGNED" : "ALL",
          scopeObjectId: null,
        },
      ],
    },
  });
  created.push({ ...user, password });
  return { ...user, password };
}
const browser = await chromium.launch({ channel: "msedge", headless: true });
const errors = [];
async function verify(user) {
  const context = await browser.newContext({
      viewport: { width: 1280, height: 800 },
    }),
    page = await context.newPage();
  const failed = [];
  page.on("response", (r) => {
    if (r.status() >= 400 && !r.url().endsWith("/favicon.ico"))
      failed.push(`${r.status()} ${r.url()}`);
  });
  await page.goto(`${web}/login`, { waitUntil: "networkidle" });
  await page.getByLabel("账号").fill(user.username);
  await page.getByLabel("密码").fill(user.password);
  await page.getByRole("button", { name: "登录" }).click();
  await page.waitForURL(`${web}/`);
  if (user.roles.includes("VET")) {
    await page.goto(`${web}/health`, { waitUntil: "networkidle" });
    if (!(await page.getByRole("button", { name: "上报健康异常" }).count()))
      errors.push("real VET cannot report health");
    await page.goto(`${web}/breeding`, { waitUntil: "networkidle" });
    if (await page.getByRole("button", { name: "记录发情" }).count())
      errors.push("real VET can write breeding");
  }
  if (user.roles.includes("BREEDER")) {
    await page.goto(`${web}/breeding`, { waitUntil: "networkidle" });
    if (!(await page.getByRole("button", { name: "记录发情" }).count()))
      errors.push("real BREEDER cannot write breeding");
    await page.goto(`${web}/health`, { waitUntil: "networkidle" });
    if (await page.getByRole("button", { name: "上报健康异常" }).count())
      errors.push("real BREEDER can report health");
  }
  if (user.roles.includes("WORKER")) {
    await page.goto(`${web}/health`, { waitUntil: "networkidle" });
    if (!(await page.getByRole("button", { name: "上报健康异常" }).count()))
      errors.push("real WORKER cannot report health");
    await page.goto(`${web}/feeding`, { waitUntil: "networkidle" });
    if (await page.getByRole("tab", { name: "原料档案" }).count())
      errors.push("real WORKER sees ingredient tab");
    await page.goto(`${web}/reports`, { waitUntil: "networkidle" });
    if (new URL(page.url()).pathname !== "/")
      errors.push("real WORKER can access reports");
  }
  if (failed.length)
    errors.push(`${user.roles[0]} HTTP failures: ${failed.join(", ")}`);
  await context.close();
}
try {
  const users = await Promise.all(["VET", "BREEDER", "WORKER"].map(create));
  for (const user of users) await verify(user);
} finally {
  for (const user of created) {
    try {
      await request(`/users/${user.userId}`, {
        token: admin.accessToken,
        method: "PUT",
        body: {
          realName: user.realName,
          phone: user.phone || undefined,
          roleCodes: user.roles,
          dataScopes: user.dataScopes,
          status: "DISABLED",
          version: user.version,
        },
      });
    } catch (error) {
      errors.push(`cleanup ${user.username}: ${error.message}`);
    }
  }
  await browser.close();
}
if (errors.length) {
  console.error(errors.join("\n"));
  process.exit(1);
}
console.log(
  "Real-role E2E passed: VET, BREEDER, WORKER; temporary accounts disabled",
);
