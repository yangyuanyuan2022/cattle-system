const api = process.env.API_BASE_URL || "http://127.0.0.1:8080/api/v1";
const stamp = Date.now();

async function request(path, { token, method = "GET", body, form, binary = false } = {}) {
  const response = await fetch(api + path, {
    method,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(body ? { "Content-Type": "application/json", "X-Idempotency-Key": crypto.randomUUID() } : {}),
      ...(form ? { "X-Idempotency-Key": crypto.randomUUID() } : {}),
    },
    body: form || (body ? JSON.stringify(body) : undefined),
  });
  if (binary) {
    if (!response.ok) throw new Error(`${method} ${path}: ${response.status}`);
    return new Uint8Array(await response.arrayBuffer());
  }
  const payload = await response.json().catch(() => null);
  if (!response.ok || payload?.code !== "SUCCESS") throw new Error(`${method} ${path}: ${response.status} ${payload?.message || ""}`);
  return payload.data;
}

const admin = await request("/auth/login", { method: "POST", body: { username: "admin", password: "123456" } });
const token = admin.accessToken;
let farmOriginal, farmCurrent, rulesOriginal, barnOriginal, barnCurrent, herdOriginal, herdCurrent;
let dictionary, role, user, order;
const completed = [];
try {
  farmOriginal = await request("/farm", { token });
  farmCurrent = await request("/farm", { token, method: "PUT", body: { farmName: farmOriginal.farmName, contactName: farmOriginal.contactName, contactPhone: farmOriginal.contactPhone, remark: `E2E save ${stamp}`, version: farmOriginal.version } });
  completed.push("farm save/restore");

  rulesOriginal = await request("/settings/business-rules", { token });
  const values = Object.fromEntries(rulesOriginal.map((rule) => [rule.code, rule.value]));
  const firstRule = rulesOriginal[0];
  if (firstRule) await request("/settings/business-rules", { token, method: "PUT", body: { values: { ...values, [firstRule.code]: firstRule.value } } });
  completed.push("business rules save/restore");

  dictionary = await request("/dictionaries/entries", { token, method: "POST", body: { typeCode: "CATTLE_BREED", itemCode: `E2E_${stamp}`, itemName: "E2E 临时品种", sortNo: 999, remark: "自动化验收，完成后停用" } });
  completed.push("dictionary create/disable");

  const roles = await request("/roles", { token });
  const baseRole = roles.find((item) => item.roleCode === "WORKER") || roles[0];
  role = await request("/roles", { token, method: "POST", body: { roleCode: `E2E_ROLE_${stamp}`, roleName: "E2E 临时角色", permissionCodes: baseRole.permissionCodes } });
  user = await request("/users", { token, method: "POST", body: { username: `e2e_write_${stamp}`, realName: "E2E 写操作用户", password: "Test123456!", roleCodes: [role.roleCode], dataScopes: [{ scopeType: "ALL", scopeObjectId: null }] } });
  completed.push("role/user create/disable");

  const barns = await request("/barns", { token });
  barnOriginal = barns[0];
  barnCurrent = await request(`/barns/${barnOriginal.barnId}`, { token, method: "PUT", body: { barnName: `${barnOriginal.barnName} E2E`, barnType: barnOriginal.barnType, capacity: barnOriginal.capacity, status: barnOriginal.status, remark: barnOriginal.remark, version: barnOriginal.version } });
  const herds = await request("/herds", { token });
  herdOriginal = herds.find((item) => item.barnId === barnOriginal.barnId) || herds[0];
  herdCurrent = await request(`/herds/${herdOriginal.herdId}`, { token, method: "PUT", body: { herdName: `${herdOriginal.herdName} E2E`, herdType: herdOriginal.herdType, barnId: herdOriginal.barnId, status: herdOriginal.status, remark: herdOriginal.remark, version: herdOriginal.version } });
  completed.push("barn/herd edit/restore");

  const formulas = await request("/feeding/ration-formulas", { token });
  const formula = formulas.find((item) => item.status === "ACTIVE");
  if (!formula) throw new Error("No active ration formula available for calculation test");
  order = await request("/feeding/mixing-orders/calculate", { token, method: "POST", body: { formulaId: formula.formulaId, targetHerdId: herdOriginal.herdId, assigneeId: admin.userId, cattleCount: 1, feedDate: new Date().toISOString().slice(0, 10) } });
  order = await request(`/feeding/mixing-orders/${order.orderId}/cancel`, { token, method: "POST", body: { reason: "自动化验收后取消", version: order.version } });
  completed.push("mixing calculation/cancel");

  const cattlePage = await request("/cattle?page=1&pageSize=20&keyword=IMPORT-", { token });
  const cattle = cattlePage.items.find((item) => item.presenceStatus === "EXITED") || cattlePage.items[0];
  if (!cattle) throw new Error("No archived import-test cattle available for attachment test");
  const bytes = new TextEncoder().encode(`attachment-e2e-${stamp}`);
  const form = new FormData();
  form.append("businessType", "CATTLE");
  form.append("businessId", cattle.cattleId);
  form.append("file", new Blob([bytes], { type: "text/csv" }), `attachment-e2e-${stamp}.csv`);
  const attachment = await request("/attachments", { token, method: "POST", form });
  const downloaded = await request(`/attachments/${attachment.attachmentId}/content`, { token, binary: true });
  if (new TextDecoder().decode(downloaded) !== new TextDecoder().decode(bytes)) throw new Error("attachment download content mismatch");
  completed.push("attachment upload/download");
} finally {
  const cleanupErrors = [];
  async function cleanup(action) { try { await action(); } catch (error) { cleanupErrors.push(error.message); } }
  if (order && order.status !== "CANCELLED") await cleanup(async () => { order = await request(`/feeding/mixing-orders/${order.orderId}/cancel`, { token, method: "POST", body: { reason: "自动化异常清理", version: order.version } }); });
  if (herdOriginal && herdCurrent) await cleanup(() => request(`/herds/${herdOriginal.herdId}`, { token, method: "PUT", body: { herdName: herdOriginal.herdName, herdType: herdOriginal.herdType, barnId: herdOriginal.barnId, status: herdOriginal.status, remark: herdOriginal.remark, version: herdCurrent.version } }));
  if (barnOriginal && barnCurrent) await cleanup(() => request(`/barns/${barnOriginal.barnId}`, { token, method: "PUT", body: { barnName: barnOriginal.barnName, barnType: barnOriginal.barnType, capacity: barnOriginal.capacity, status: barnOriginal.status, remark: barnOriginal.remark, version: barnCurrent.version } }));
  if (user) await cleanup(() => request(`/users/${user.userId}`, { token, method: "PUT", body: { realName: user.realName, phone: user.phone, status: "DISABLED", roleCodes: user.roles, dataScopes: user.dataScopes, version: user.version } }));
  if (role) await cleanup(() => request(`/roles/${role.roleId}`, { token, method: "PUT", body: { roleName: role.roleName, status: "DISABLED", permissionCodes: role.permissionCodes, version: role.version } }));
  if (dictionary) await cleanup(() => request(`/dictionaries/entries/${dictionary.itemId}`, { token, method: "PUT", body: { itemName: dictionary.itemName, status: "DISABLED", sortNo: dictionary.sortNo, remark: dictionary.remark } }));
  if (rulesOriginal) await cleanup(() => request("/settings/business-rules", { token, method: "PUT", body: { values: Object.fromEntries(rulesOriginal.map((rule) => [rule.code, rule.value])) } }));
  if (farmOriginal && farmCurrent) await cleanup(() => request("/farm", { token, method: "PUT", body: { farmName: farmOriginal.farmName, contactName: farmOriginal.contactName, contactPhone: farmOriginal.contactPhone, remark: farmOriginal.remark, version: farmCurrent.version } }));
  if (cleanupErrors.length) throw new Error(`cleanup failed: ${cleanupErrors.join("; ")}`);
}
console.log(`Write-operation E2E passed: ${completed.join(", ")}`);
