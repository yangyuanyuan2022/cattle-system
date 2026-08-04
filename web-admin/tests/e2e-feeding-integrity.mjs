const api = process.env.API_BASE_URL || "http://127.0.0.1:8080/api/v1";
const stamp = Date.now();

async function request(path, { token, method = "GET", body, expectFailure = false } = {}) {
  const response = await fetch(api + path, {
    method,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(body ? { "Content-Type": "application/json" } : {}),
      ...(method !== "GET" ? { "X-Idempotency-Key": crypto.randomUUID() } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const payload = await response.json().catch(() => null);
  if (expectFailure) {
    if (response.ok && payload?.code === "SUCCESS") throw new Error(`${method} ${path} unexpectedly succeeded`);
    return { response, payload };
  }
  if (!response.ok || payload?.code !== "SUCCESS")
    throw new Error(`${method} ${path}: ${response.status} ${payload?.message || ""}`);
  return payload.data;
}

function close(actual, expected, label, tolerance = 0.001) {
  if (Math.abs(Number(actual) - expected) > tolerance)
    throw new Error(`${label}: expected ${expected}, got ${actual}`);
}

const login = await request("/auth/login", { method: "POST", body: { username: "admin", password: "123456" } });
const token = login.accessToken;
let ingredient;
let formula;
let updatedIngredient;

try {
  ingredient = await request("/feeding/ingredients", {
    token,
    method: "POST",
    body: {
      ingredientName: `E2E snapshot ingredient ${stamp}`,
      ingredientType: "ENERGY",
      dryMatterPct: 90,
      tdnPct: 70,
      metabolizableEnergyValue: 2.5,
      crudeProteinPct: 12,
      starchPct: 45,
      energyValue: 1.6,
      gainEnergyValue: 1.0,
      ndfPct: 30,
      peNdfPct: 15,
      adfPct: 18,
      ashPct: 5,
      crudeFatPct: 3,
      calciumPct: 0.2,
      phosphorusPct: 0.1,
      rdpPct: 60,
      unitPrice: 2,
      remark: "E2E history snapshot verification",
    },
  });

  formula = await request("/feeding/ration-formulas", {
    token,
    method: "POST",
    body: {
      formulaName: `E2E snapshot ration ${stamp}`,
      targetType: "CUSTOM",
      dailyIntakeKg: 1,
      remark: "E2E history snapshot verification",
      items: [{ ingredientId: ingredient.ingredientId, ratioPct: 100, dailyAmountKg: 1 }],
    },
  });

  const draftOrderAttempt = await request("/feeding/mixing-orders", {
    token,
    method: "POST",
    body: { formulaId: formula.formulaId, assigneeId: login.userId, cattleCount: 2, feedDate: new Date().toISOString().slice(0, 10) },
    expectFailure: true,
  });
  if (draftOrderAttempt.response.status !== 409) throw new Error(`Draft formula rejection returned ${draftOrderAttempt.response.status}`);

  formula = await request(`/feeding/ration-formulas/${formula.formulaId}/activate`, {
    token,
    method: "POST",
    body: { reason: "E2E activate", version: formula.rowVersion },
  });

  let order = await request("/feeding/mixing-orders", {
    token,
    method: "POST",
    body: { formulaId: formula.formulaId, assigneeId: login.userId, cattleCount: 2, feedDate: new Date().toISOString().slice(0, 10) },
  });
  close(order.totalCost, 4, "Initial order cost");
  close(order.items[0].unitPrice, 2, "Initial order snapshot price");

  updatedIngredient = await request(`/feeding/ingredients/${ingredient.ingredientId}`, {
    token,
    method: "PUT",
    body: {
      ...ingredient,
      dryMatterPct: 80,
      crudeProteinPct: 30,
      calciumPct: 1,
      phosphorusPct: 0.5,
      unitPrice: 9,
      status: "ENABLED",
      version: ingredient.version,
    },
  });

  const formulas = await request("/feeding/ration-formulas", { token });
  const historicalFormula = formulas.find((item) => item.formulaId === formula.formulaId);
  if (!historicalFormula) throw new Error("Historical formula not found");
  close(historicalFormula.dailyCost, 2, "Formula snapshot cost");
  close(historicalFormula.crudeProteinPct, 12, "Formula snapshot crude protein");
  close(historicalFormula.nutrition.calciumPct, 0.2, "Formula snapshot calcium");
  close(historicalFormula.items[0].unitPrice, 2, "Formula line snapshot price");

  order = await request(`/feeding/mixing-orders/${order.orderId}`, { token });
  close(order.totalCost, 4, "Order historical cost after ingredient update");
  close(order.items[0].unitPrice, 2, "Order historical price after ingredient update");

  const micronutrients = await request("/feeding/micronutrients/recommend", {
    token,
    method: "POST",
    body: { productionStage: "GROWING", dryMatterIntakeKg: 0.9, cattleCount: 2, formulaId: formula.formulaId },
  });
  const calcium = micronutrients.items.find((item) => item.nutrientName === "钙");
  const phosphorus = micronutrients.items.find((item) => item.nutrientName === "磷");
  close(calcium.actualDailyPerHead, 1.8, "Calcium actual supply from snapshot");
  close(calcium.gapToMinPerHead, 0.9, "Calcium gap to daily minimum");
  close(phosphorus.actualDailyPerHead, 0.9, "Phosphorus actual supply from snapshot");

  order = await request(`/feeding/mixing-orders/${order.orderId}/confirm`, {
    token,
    method: "POST",
    body: { reason: "E2E confirm", version: order.version },
  });
  order = await request(`/feeding/mixing-orders/${order.orderId}/execute`, {
    token,
    method: "POST",
    body: {
      executionTime: new Date().toISOString().slice(0, 19),
      deviationNote: "E2E actual feed retained after void",
      version: order.version,
      items: [{ ingredientId: ingredient.ingredientId, actualAmountKg: 2 }],
    },
  });

  let executionPage = await request("/feeding/mixing-executions/page?page=1&pageSize=100", { token });
  let execution = executionPage.items.find((item) => item.orderId === order.orderId && item.status === "EXECUTED");
  if (!execution || !execution.actualSummary.includes("2kg")) throw new Error("Executed record or actual feed summary missing");

  await request(`/feeding/mixing-executions/${execution.executionId}/void`, {
    token,
    method: "POST",
    body: { reason: "E2E void reason", version: execution.orderVersion },
  });
  executionPage = await request("/feeding/mixing-executions/page?page=1&pageSize=100", { token });
  execution = executionPage.items.find((item) => item.executionId === execution.executionId);
  if (execution?.status !== "VOIDED") throw new Error("Execution was not retained as VOIDED");
  if (execution.voidReason !== "E2E void reason" || !execution.actualSummary.includes("2kg"))
    throw new Error("Voided execution lost reason or original actual feed data");

  order = await request(`/feeding/mixing-orders/${order.orderId}`, { token });
  if (order.status !== "CONFIRMED") throw new Error(`Voided order should return to CONFIRMED, got ${order.status}`);
  const orderDeleteAttempt = await request(`/feeding/mixing-orders/${order.orderId}`, { token, method: "DELETE", expectFailure: true });
  if (orderDeleteAttempt.response.status !== 409) throw new Error("Order with execution history should not be deletable");

  const ingredientPage = await request("/feeding/ingredients/page?page=1&pageSize=25", { token });
  const orderPage = await request("/feeding/mixing-orders/page?page=1&pageSize=25", { token });
  if (ingredientPage.items.length > 25 || ingredientPage.total < ingredientPage.items.length) throw new Error("Ingredient pagination contract failed");
  const filteredIngredientPage = await request(`/feeding/ingredients/page?page=1&pageSize=25&keyword=${encodeURIComponent(updatedIngredient.ingredientName)}&ingredientType=ENERGY`, { token });
  if (filteredIngredientPage.total !== 1 || filteredIngredientPage.items[0]?.ingredientId !== ingredient.ingredientId)
    throw new Error("Ingredient pagination did not search the full filtered data set");
  if (filteredIngredientPage.typeCounts?.ENERGY !== 1)
    throw new Error("Ingredient type facets did not count the full filtered data set");
  const wrongTypeIngredientPage = await request(`/feeding/ingredients/page?page=1&pageSize=25&keyword=${encodeURIComponent(updatedIngredient.ingredientName)}&ingredientType=ROUGHAGE`, { token });
  if (wrongTypeIngredientPage.total !== 0 || wrongTypeIngredientPage.items.length !== 0)
    throw new Error("Ingredient type filter was not applied by the server");
  if (wrongTypeIngredientPage.typeCounts?.ENERGY !== 1)
    throw new Error("Ingredient type facets should remain available when switching filters");
  if (orderPage.items.length > 25 || orderPage.total < orderPage.items.length) throw new Error("Order pagination contract failed");
  if (executionPage.items.length > 100 || executionPage.total < executionPage.items.length) throw new Error("Execution pagination contract failed");

  formula = await request(`/feeding/ration-formulas/${formula.formulaId}/deactivate`, {
    token,
    method: "POST",
    body: { reason: "E2E cleanup archive", version: formula.rowVersion },
  });
  updatedIngredient = await request(`/feeding/ingredients/${ingredient.ingredientId}`, {
    token,
    method: "PUT",
    body: { ...updatedIngredient, status: "DISABLED", version: updatedIngredient.version },
  });
} finally {
  if (formula?.status === "ACTIVE") {
    await request(`/feeding/ration-formulas/${formula.formulaId}/deactivate`, {
      token,
      method: "POST",
      body: { reason: "E2E failure cleanup", version: formula.rowVersion },
    }).catch(() => {});
  }
  if (ingredient && updatedIngredient?.status !== "DISABLED") {
    await request(`/feeding/ingredients/${ingredient.ingredientId}`, {
      token,
      method: "PUT",
      body: { ...(updatedIngredient || ingredient), status: "DISABLED", version: (updatedIngredient || ingredient).version },
    }).catch(() => {});
  }
}

console.log("Feeding integrity E2E passed: immutable snapshots, active-only production, void retention, mineral gaps, and pagination");
