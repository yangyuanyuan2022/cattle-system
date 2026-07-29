<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  getAllCattle,
  getCattleDetail,
  type CattleRecord,
} from "../api/cattle";
import {
  getBreedingEvents,
  getBreedingRecords,
  getDueCows,
  recordBreeding,
  recordCalving,
  recordEstrus,
  recordPregnancy,
  type BreedingEvent,
  type DueCow,
  type BreedingRecord,
  type BreedingRecordKind,
  voidBreedingRecord,
} from "../api/breeding";
import { getStoredUser } from "../auth/session";
const canWrite =
  getStoredUser()?.roles.some((role) =>
    ["ADMIN", "FARM_MANAGER", "BREEDER"].includes(role),
  ) ?? false;
const cows = ref<CattleRecord[]>([]),
  dueCows = ref<DueCow[]>([]),
  cowId = ref(""),
  events = ref<BreedingEvent[]>([]),
  records = ref<(BreedingRecord & { kind: BreedingRecordKind; label: string; id: string; date: string; summary: string })[]>([]),
  loading = ref(false),
  saving = ref(false),
  dialog = ref("");
const now = () => new Date().toISOString().slice(0, 16),
  today = () => new Date().toISOString().slice(0, 10);
const current = computed(() =>
  cows.value.find((x) => x.cattleId === cowId.value),
);
const statusLabels: any = {
  WAIT_BREED: "待配",
  BRED_WAIT_CHECK: "已配待检",
  PREGNANT: "妊娠",
  NEAR_CALVING: "临产",
  POSTPARTUM: "产后恢复",
};
const estrus = reactive({ estrusTime: now(), symptoms: "", suggestion: "" }),
  breed = reactive({
    breedingDate: now(),
    breedingMethod: "AI",
    semenOrBull: "",
    breedingTimes: 1,
    remark: "",
  }),
  pregnancy = reactive({
    checkDate: now(),
    checkResult: "POSITIVE",
    expectedCalvingDate: "",
    remark: "",
  }),
  calving = reactive({
    calvingDate: now(),
    difficultyLevel: "NORMAL",
    calfCount: 1,
    aliveCount: 1,
    damCondition: "正常",
    remark: "",
    earTagNo: "",
    calfSex: "FEMALE",
    birthWeight: 35,
    survivalStatus: "ALIVE",
  });
async function init() {
  try {
    const [page, due] = await Promise.all([
      getAllCattle({ presenceStatus: "IN_FIELD" }),
      getDueCows(30),
    ]);
    const all = page;
    dueCows.value = due;
    cows.value = all.filter(
      (c) =>
        c.sex === "FEMALE" && ["RESERVE", "COW"].includes(c.lifecycleStage),
    );
    if (cows.value.length) {
      cowId.value = cows.value[0].cattleId;
      await load();
    }
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || "母牛档案加载失败");
  }
}
async function load() {
  if (!cowId.value) return;
  loading.value = true;
  try {
    const [timeline, heats, inseminations, checks, calvings] = await Promise.all([
      getBreedingEvents(cowId.value),
      getBreedingRecords("heats", cowId.value),
      getBreedingRecords("inseminations", cowId.value),
      getBreedingRecords("pregnancy-checks", cowId.value),
      getBreedingRecords("calvings", cowId.value),
    ]);
    events.value = timeline;
    records.value = [
      ...heats.map((x) => ({ ...x, kind: "heats" as const, label: "发情", id: x.heatId!, date: x.heatTime!, summary: x.symptoms || "发情记录" })),
      ...inseminations.map((x) => ({ ...x, kind: "inseminations" as const, label: "配种", id: x.inseminationId!, date: x.inseminationDate!, summary: x.semenOrBull || "配种记录" })),
      ...checks.map((x) => ({ ...x, kind: "pregnancy-checks" as const, label: "妊检", id: x.checkId!, date: x.checkDate!, summary: x.result || "妊检记录" })),
      ...calvings.map((x) => ({ ...x, kind: "calvings" as const, label: "产犊", id: x.calvingId!, date: x.calvingDate!, summary: x.difficultyLevel || "产犊记录" })),
    ].sort((a, b) => b.date.localeCompare(a.date));
    const fresh = await getCattleDetail(cowId.value);
    if (fresh) {
      const i = cows.value.findIndex((c) => c.cattleId === cowId.value);
      cows.value[i] = fresh;
    }
  } finally {
    loading.value = false;
  }
}
async function voidRecord(row: (typeof records.value)[number]) {
  try {
    const { value } = await ElMessageBox.prompt("请输入作废原因。作废后记录仍可在审计日志中追溯。", `作废${row.label}记录`, { inputValidator: (v) => !!v.trim() || "必须填写作废原因", confirmButtonText: "确认作废", cancelButtonText: "取消" });
    await voidBreedingRecord(row.kind, row.id, row.version, value.trim());
    ElMessage.success("记录已作废");
    await load();
  } catch (e: any) {
    if (e !== "cancel" && e !== "close") ElMessage.error(e.response?.data?.message || "作废失败");
  }
}
function selectDueCow(row: DueCow) {
  cowId.value = row.cattleId;
  load();
}
function open(type: string) {
  dialog.value = type;
  if (type === "estrus")
    Object.assign(estrus, { estrusTime: now(), symptoms: "", suggestion: "" });
  if (type === "breed")
    Object.assign(breed, {
      breedingDate: now(),
      breedingMethod: "AI",
      semenOrBull: "",
      breedingTimes: 1,
      remark: "",
    });
  if (type === "pregnancy")
    Object.assign(pregnancy, {
      checkDate: now(),
      checkResult: "POSITIVE",
      expectedCalvingDate: "",
      remark: "",
    });
  if (type === "calving")
    Object.assign(calving, {
      calvingDate: now(),
      difficultyLevel: "NORMAL",
      calfCount: 1,
      aliveCount: 1,
      damCondition: "正常",
      remark: "",
      earTagNo: "",
      calfSex: "FEMALE",
      birthWeight: 35,
      survivalStatus: "ALIVE",
    });
}
async function save() {
  if (!cowId.value) return;
  saving.value = true;
  try {
    if (dialog.value === "estrus")
      await recordEstrus(
        {
          cattleId: cowId.value,
          ...estrus,
          estrusTime: estrus.estrusTime + ":00",
        },
        crypto.randomUUID(),
      );
    if (dialog.value === "breed") {
      if (!breed.semenOrBull) throw new Error("请填写精液编号或公牛信息");
      await recordBreeding(
        {
          cattleId: cowId.value,
          ...breed,
          breedingDate: breed.breedingDate + ":00",
        },
        crypto.randomUUID(),
      );
    }
    if (dialog.value === "pregnancy")
      await recordPregnancy(
        {
          cattleId: cowId.value,
          ...pregnancy,
          checkDate: pregnancy.checkDate + ":00",
          expectedCalvingDate: pregnancy.expectedCalvingDate || null,
        },
        crypto.randomUUID(),
      );
    if (dialog.value === "calving") {
      if (calving.aliveCount && !calving.earTagNo)
        throw new Error("存活犊牛必须填写耳号");
      const calves = calving.aliveCount
        ? [
            {
              earTagNo: calving.earTagNo,
              sex: calving.calfSex,
              birthWeight: calving.birthWeight,
              survivalStatus: calving.survivalStatus,
            },
          ]
        : [];
      await recordCalving(
        {
          damCattleId: cowId.value,
          calvingDate: calving.calvingDate + ":00",
          difficultyLevel: calving.difficultyLevel,
          calfCount: calving.calfCount,
          aliveCount: calving.aliveCount,
          damCondition: calving.damCondition,
          remark: calving.remark,
          calves,
        },
        crypto.randomUUID(),
      );
    }
    ElMessage.success("繁育记录已保存");
    dialog.value = "";
    await load();
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || "保存失败");
  } finally {
    saving.value = false;
  }
}
onMounted(init);
</script>
<template>
  <div>
    <div class="page-heading">
      <div>
        <p class="eyebrow">繁育管理</p>
        <h1>母牛繁育档案</h1>
      </div>
    </div>
    <section class="breeding-toolbar">
      <div>
        <label>母牛</label
        ><el-select v-model="cowId" filterable @change="load"
          ><el-option
            v-for="c in cows"
            :key="c.cattleId"
            :label="`${c.earTagNo} ${c.name || ''}`"
            :value="c.cattleId"
        /></el-select>
      </div>
      <el-tag size="large" effect="plain">{{
        statusLabels[current?.breedingStatus || ""] || "未进入繁育流程"
      }}</el-tag>
    </section>
    <section v-if="canWrite" class="breeding-actions">
      <el-button @click="open('estrus')">记录发情</el-button
      ><el-button type="primary" plain @click="open('breed')"
        >登记配种</el-button
      ><el-button type="warning" plain @click="open('pregnancy')"
        >登记妊检</el-button
      ><el-button type="success" plain @click="open('calving')"
        >登记产犊</el-button
      >
    </section>
    <section class="table-panel due-panel">
      <div class="panel-heading">
        <div>
          <h2>30 天内待产</h2>
          <p>按预计产犊日排序，便于提前安排巡检</p>
        </div>
        <el-tag type="warning" effect="plain">{{ dueCows.length }} 头</el-tag>
      </div>
      <el-table
        :data="dueCows"
        empty-text="未来 30 天暂无待产母牛"
        @row-click="selectDueCow"
      >
        <el-table-column prop="earTagNo" label="耳号" min-width="140" />
        <el-table-column prop="cattleName" label="名称" min-width="100"
          ><template #default="s">{{
            s.row.cattleName || "-"
          }}</template></el-table-column
        >
        <el-table-column label="位置" min-width="160"
          ><template #default="s"
            >{{ s.row.barnName || "-" }} / {{ s.row.herdName || "-" }}</template
          ></el-table-column
        >
        <el-table-column
          prop="expectedCalvingDate"
          label="预计产犊日"
          width="125"
        />
        <el-table-column label="剩余" width="90"
          ><template #default="s"
            ><strong>{{ s.row.daysUntilCalving }} 天</strong></template
          ></el-table-column
        >
      </el-table>
    </section>
    <section class="table-panel">
      <div class="panel-heading">
        <div>
          <h2>繁育时间轴</h2>
          <p>关键操作同步进入牛只完整生命周期</p>
        </div>
      </div>
      <el-table v-loading="loading" :data="events" empty-text="暂无繁育记录"
        ><el-table-column
          prop="eventDate"
          label="时间"
          min-width="180"
        /><el-table-column
          prop="summary"
          label="事件"
          min-width="260"
        /><el-table-column label="状态" width="110"
          ><template #default="s">{{
            statusLabels[s.row.breedingStatus] || "-"
          }}</template></el-table-column
      ></el-table
      >
    </section>
    <section class="table-panel">
      <div class="panel-heading"><div><h2>业务记录</h2><p>按时间倒序展示，可按业务依赖关系逐条作废</p></div></div>
      <el-table :data="records" empty-text="暂无可操作记录">
        <el-table-column prop="date" label="时间" min-width="180" />
        <el-table-column prop="label" label="类型" width="90" />
        <el-table-column prop="summary" label="摘要" min-width="220" show-overflow-tooltip />
        <el-table-column v-if="canWrite" label="操作" width="90" fixed="right">
          <template #default="s"><el-button v-if="s.row.kind !== 'calvings' || getStoredUser()?.roles.some((r) => ['ADMIN','FARM_MANAGER'].includes(r))" link type="danger" @click="voidRecord(s.row)">作废</el-button></template>
        </el-table-column>
      </el-table>
    </section>
    <el-dialog
      :model-value="!!dialog"
      :title="
        {
          estrus: '记录发情',
          breed: '登记配种',
          pregnancy: '登记妊检',
          calving: '登记产犊',
        }[dialog]
      "
      width="min(580px,94vw)"
      @close="dialog = ''"
    >
      <el-form v-if="dialog === 'estrus'" label-position="top"
        ><el-form-item label="发现时间"
          ><el-date-picker
            v-model="estrus.estrusTime"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm" /></el-form-item
        ><el-form-item label="发情表现"
          ><el-input v-model="estrus.symptoms" type="textarea" /></el-form-item
        ><el-form-item label="处理建议"
          ><el-input v-model="estrus.suggestion" /></el-form-item
      ></el-form>
      <el-form v-if="dialog === 'breed'" label-position="top"
        ><el-form-item label="配种时间"
          ><el-date-picker
            v-model="breed.breedingDate"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm" /></el-form-item
        ><el-form-item label="配种方式"
          ><el-segmented
            v-model="breed.breedingMethod"
            :options="[
              { label: '人工授精', value: 'AI' },
              { label: '自然交配', value: 'NATURAL' },
            ]" /></el-form-item
        ><el-form-item label="精液编号 / 公牛信息" required
          ><el-input v-model="breed.semenOrBull" /></el-form-item
        ><el-form-item label="配种次数"
          ><el-input-number
            v-model="breed.breedingTimes"
            :min="1" /></el-form-item
        ><el-form-item label="备注"
          ><el-input v-model="breed.remark" type="textarea" /></el-form-item
      ></el-form>
      <el-form v-if="dialog === 'pregnancy'" label-position="top"
        ><el-form-item label="检查时间"
          ><el-date-picker
            v-model="pregnancy.checkDate"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm" /></el-form-item
        ><el-form-item label="检查结果"
          ><el-select v-model="pregnancy.checkResult"
            ><el-option label="阳性" value="POSITIVE" /><el-option
              label="阴性"
              value="NEGATIVE" /><el-option
              label="待复查"
              value="RECHECK" /></el-select></el-form-item
        ><el-form-item
          v-if="pregnancy.checkResult === 'POSITIVE'"
          label="预计产犊日"
          ><el-date-picker
            v-model="pregnancy.expectedCalvingDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="不填则按配种日+283天" /></el-form-item
        ><el-form-item label="备注"
          ><el-input v-model="pregnancy.remark" type="textarea" /></el-form-item
      ></el-form>
      <el-form
        v-if="dialog === 'calving'"
        label-position="top"
        class="calving-form"
        ><el-form-item label="产犊时间"
          ><el-date-picker
            v-model="calving.calvingDate"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm" /></el-form-item
        ><el-form-item label="难产程度"
          ><el-select v-model="calving.difficultyLevel"
            ><el-option label="正常" value="NORMAL" /><el-option
              label="助产"
              value="ASSISTED" /><el-option
              label="难产"
              value="DIFFICULT" /></el-select></el-form-item
        ><el-form-item label="犊牛总数"
          ><el-input-number
            v-model="calving.calfCount"
            :min="1" /></el-form-item
        ><el-form-item label="存活数"
          ><el-input-number
            v-model="calving.aliveCount"
            :min="0"
            :max="1" /></el-form-item
        ><el-divider content-position="left">存活犊牛档案</el-divider
        ><template v-if="calving.aliveCount"
          ><el-form-item label="犊牛耳号" required
            ><el-input v-model="calving.earTagNo" /></el-form-item
          ><el-form-item label="性别"
            ><el-segmented
              v-model="calving.calfSex"
              :options="[
                { label: '母', value: 'FEMALE' },
                { label: '公', value: 'MALE' },
              ]" /></el-form-item
          ><el-form-item label="出生体重(kg)"
            ><el-input-number
              v-model="calving.birthWeight"
              :min="0.01" /></el-form-item
          ><el-form-item label="存活状态"
            ><el-select v-model="calving.survivalStatus"
              ><el-option label="正常存活" value="ALIVE" /><el-option
                label="弱犊"
                value="WEAK" /></el-select></el-form-item></template
        ><el-form-item label="母牛情况"
          ><el-input v-model="calving.damCondition" /></el-form-item
        ><el-form-item label="备注"
          ><el-input v-model="calving.remark" type="textarea" /></el-form-item
      ></el-form>
      <template #footer
        ><el-button @click="dialog = ''">取消</el-button
        ><el-button type="primary" :loading="saving" @click="save"
          >保存</el-button
        ></template
      ></el-dialog
    >
  </div>
</template>
<style scoped>
.breeding-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: end;
  background: #fff;
  border: 1px solid #dde3df;
  padding: 16px 18px;
}
.breeding-toolbar > div {
  display: grid;
  gap: 7px;
}
.breeding-toolbar label {
  font-size: 13px;
  color: #697570;
}
.breeding-toolbar .el-select {
  width: min(360px, 70vw);
}
.breeding-actions {
  display: flex;
  gap: 10px;
  padding: 14px 18px;
  background: #fff;
  border: 1px solid #dde3df;
  border-top: 0;
  margin-bottom: 16px;
}
.el-select,
.el-date-editor {
  width: 100%;
}
.calving-form {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}
.calving-form .el-divider {
  grid-column: 1/-1;
}
@media (max-width: 640px) {
  .breeding-actions {
    display: grid;
    grid-template-columns: 1fr 1fr;
  }
  .calving-form {
    grid-template-columns: 1fr;
  }
  .calving-form .el-divider {
    grid-column: auto;
  }
}
</style>
