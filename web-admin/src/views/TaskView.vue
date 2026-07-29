<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { Plus } from "@element-plus/icons-vue";
import { getAllCattle, type CattleRecord } from "../api/cattle";
import {
  cancelTask,
  completeTask,
  createTask,
  getTasks,
  rescheduleTask,
  type TaskItem,
} from "../api/task";
import { getStoredUser } from "../auth/session";
import { getUsers, type UserItem } from "../api/user";
const canManage =
  getStoredUser()?.roles.some((role) =>
    ["ADMIN", "FARM_MANAGER"].includes(role),
  ) ?? false;
const router = useRouter(),
  tasks = ref<TaskItem[]>([]),
  cattle = ref<CattleRecord[]>([]),
  users = ref<UserItem[]>([]),
  loading = ref(false),
  saving = ref(false),
  status = ref(String(useRoute().query.status || "")),
  dialog = ref(""),
  selected = ref<TaskItem | null>(null);
const today = () => new Date().toISOString().slice(0, 10);
const create = reactive({
    title: "",
    taskType: "INSPECTION",
    cattleId: "",
    assigneeId: "",
    planDate: today(),
    dueDate: today(),
    priority: "NORMAL",
  }),
  action = reactive({
    result: "",
    reason: "",
    planDate: today(),
    dueDate: today(),
  });
const typeLabels: any = {
    MIXING_EXECUTION: "配料执行",
    PREGNANCY_CHECK: "妊检",
    PREGNANCY_RECHECK: "妊检复查",
    CALVING_CHECK: "待产检查",
    CALVING_HANDLE: "产犊处理",
    HEALTH_FOLLOW_UP: "健康复查",
    VACCINATION_EXECUTION: "防疫执行",
    WEIGHT_RECORD: "称重",
    TRANSFER: "转群",
    INSPECTION: "巡检",
    OTHER: "其他",
  },
  statusLabels: any = {
    PENDING: "待处理",
    IN_PROGRESS: "进行中",
    OVERDUE: "已逾期",
    DONE: "已完成",
    CANCELLED: "已取消",
  },
  priorityLabels: any = { NORMAL: "普通", IMPORTANT: "重要", URGENT: "紧急" };
const stats = computed(() => ({
  pending: tasks.value.filter((x) =>
    ["PENDING", "IN_PROGRESS"].includes(x.status),
  ).length,
  overdue: tasks.value.filter((x) => x.status === "OVERDUE").length,
  done: tasks.value.filter((x) => x.status === "DONE").length,
}));
async function load() {
  loading.value = true;
  try {
    tasks.value = await getTasks(status.value || undefined);
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || "任务加载失败");
  } finally {
    loading.value = false;
  }
}
async function openCreate() {
  try {
    [cattle.value, users.value] = await Promise.all([
      getAllCattle({ presenceStatus: "IN_FIELD" }),
      getUsers(),
    ]);
    Object.assign(create, {
      title: "",
      taskType: "INSPECTION",
      cattleId: "",
      assigneeId: "",
      planDate: today(),
      dueDate: today(),
      priority: "NORMAL",
    });
    dialog.value = "create";
  } catch {
    ElMessage.error("牛只档案加载失败");
  }
}
function openAction(type: string, row: TaskItem) {
  selected.value = row;
  Object.assign(action, {
    result: "",
    reason: "",
    planDate: row.planDate,
    dueDate: row.dueDate,
  });
  dialog.value = type;
}
async function save() {
  saving.value = true;
  try {
    if (dialog.value === "create") {
      if (!create.title || !create.assigneeId)
        throw new Error("请填写任务标题并选择负责人");
      await createTask(
        { ...create, cattleId: create.cattleId || undefined },
        crypto.randomUUID(),
      );
    } else if (selected.value) {
      if (dialog.value === "complete")
        await completeTask(
          selected.value.taskId,
          { result: action.result, version: selected.value.version },
          crypto.randomUUID(),
        );
      if (dialog.value === "reschedule")
        await rescheduleTask(
          selected.value.taskId,
          {
            planDate: action.planDate,
            dueDate: action.dueDate,
            reason: action.reason,
            version: selected.value.version,
          },
          crypto.randomUUID(),
        );
      if (dialog.value === "cancel")
        await cancelTask(
          selected.value.taskId,
          { reason: action.reason, version: selected.value.version },
          crypto.randomUUID(),
        );
    }
    dialog.value = "";
    ElMessage.success("任务操作成功");
    await load();
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || "任务操作失败");
  } finally {
    saving.value = false;
  }
}
function go(row: TaskItem) {
  const path: any = {
    MIXING_EXECUTION: "/feeding",
    PREGNANCY_CHECK: "/breeding",
    PREGNANCY_RECHECK: "/breeding",
    CALVING_CHECK: "/breeding",
    CALVING_HANDLE: "/breeding",
    HEALTH_FOLLOW_UP: "/health",
    VACCINATION_EXECUTION: "/vaccinations",
    WEIGHT_RECORD: "/growth",
    TRANSFER: "/cattle",
  };
  router.push(path[row.taskType] || "/");
}
onMounted(load);
</script>
<template>
  <div>
    <div class="page-heading">
      <div>
        <p class="eyebrow">协作中心</p>
        <h1>任务中心</h1>
      </div>
      <el-button
        v-if="canManage"
        type="primary"
        :icon="Plus"
        @click="openCreate"
        >创建任务</el-button
      >
    </div>
    <section class="task-stats">
      <div>
        <span>待处理</span><strong>{{ stats.pending }}</strong>
      </div>
      <div class="danger">
        <span>已逾期</span><strong>{{ stats.overdue }}</strong>
      </div>
      <div>
        <span>已完成</span><strong>{{ stats.done }}</strong>
      </div>
    </section>
    <section class="filter-bar task-filter">
      <el-select
        v-model="status"
        clearable
        placeholder="任务状态"
        @change="load"
        ><el-option
          v-for="(label, value) in statusLabels"
          :key="value"
          :label="label"
          :value="value"
      /></el-select>
    </section>
    <section class="table-panel">
      <el-table v-loading="loading" :data="tasks" empty-text="暂无任务"
        ><el-table-column label="任务" min-width="240"
          ><template #default="s"
            ><strong>{{ s.row.title }}</strong
            ><small class="cell-sub"
              >{{ typeLabels[s.row.taskType] || s.row.taskType }} ·
              {{ s.row.earTagNo || "牛场任务" }}</small
            ></template
          ></el-table-column
        ><el-table-column label="日期" min-width="180"
          ><template #default="s"
            >{{ s.row.planDate }} 至 {{ s.row.dueDate }}</template
          ></el-table-column
        ><el-table-column label="优先级" width="90"
          ><template #default="s">{{
            priorityLabels[s.row.priority]
          }}</template></el-table-column
        ><el-table-column label="状态" width="100"
          ><template #default="s"
            ><el-tag
              :type="
                s.row.status === 'OVERDUE'
                  ? 'danger'
                  : s.row.status === 'DONE'
                    ? 'success'
                    : 'warning'
              "
              effect="plain"
              >{{ statusLabels[s.row.status] }}</el-tag
            ></template
          ></el-table-column
        ><el-table-column
          prop="assigneeName"
          label="负责人"
          width="110"
        /><el-table-column label="操作" width="220" fixed="right"
          ><template #default="s"
            ><template v-if="!['DONE', 'CANCELLED'].includes(s.row.status)"
              ><el-button
                v-if="['INSPECTION', 'OTHER'].includes(s.row.taskType)"
                link
                type="primary"
                @click="openAction('complete', s.row)"
                >完成</el-button
              ><el-button v-else link type="primary" @click="go(s.row)"
                >去处理</el-button
              ><el-button link @click="openAction('reschedule', s.row)"
                >改期</el-button
              ><el-button
                v-if="canManage"
                link
                type="danger"
                @click="openAction('cancel', s.row)"
                >取消</el-button
              ></template
            ><span v-else>{{ s.row.result || "-" }}</span></template
          ></el-table-column
        ></el-table
      >
    </section>
    <el-dialog
      :model-value="!!dialog"
      :title="
        {
          create: '创建任务',
          complete: '完成任务',
          reschedule: '任务改期',
          cancel: '取消任务',
        }[dialog]
      "
      width="min(520px,92vw)"
      @close="dialog = ''"
    >
      <el-form v-if="dialog === 'create'" label-position="top"
        ><el-form-item label="任务标题" required
          ><el-input v-model="create.title" /></el-form-item
        ><el-form-item label="任务类型"
          ><el-select v-model="create.taskType"
            ><el-option label="普通巡检" value="INSPECTION" /><el-option
              label="称重任务"
              value="WEIGHT_RECORD" /><el-option
              label="转群任务"
              value="TRANSFER" /><el-option
              label="其他"
              value="OTHER" /></el-select></el-form-item
        ><el-form-item label="关联牛只"
          ><el-select v-model="create.cattleId" clearable filterable
            ><el-option
              v-for="c in cattle"
              :key="c.cattleId"
              :label="`${c.earTagNo} ${c.name || ''}`"
              :value="c.cattleId" /></el-select></el-form-item
        ><el-form-item label="负责人" required
          ><el-select v-model="create.assigneeId" filterable
            ><el-option
              v-for="u in users.filter((item) => item.status === 'ENABLED')"
              :key="u.userId"
              :label="`${u.realName}（${u.username}）`"
              :value="u.userId" /></el-select></el-form-item
        ><el-form-item label="计划日期"
          ><el-date-picker
            v-model="create.planDate"
            type="date"
            value-format="YYYY-MM-DD" /></el-form-item
        ><el-form-item label="截止日期"
          ><el-date-picker
            v-model="create.dueDate"
            type="date"
            value-format="YYYY-MM-DD" /></el-form-item
        ><el-form-item label="优先级"
          ><el-segmented
            v-model="create.priority"
            :options="[
              { label: '普通', value: 'NORMAL' },
              { label: '重要', value: 'IMPORTANT' },
              { label: '紧急', value: 'URGENT' },
            ]" /></el-form-item
      ></el-form>
      <el-form v-else label-position="top"
        ><template v-if="dialog === 'complete'"
          ><el-form-item label="完成结果" required
            ><el-input
              v-model="action.result"
              type="textarea" /></el-form-item></template
        ><template v-if="dialog === 'reschedule'"
          ><el-form-item label="计划日期"
            ><el-date-picker
              v-model="action.planDate"
              type="date"
              value-format="YYYY-MM-DD" /></el-form-item
          ><el-form-item label="截止日期"
            ><el-date-picker
              v-model="action.dueDate"
              type="date"
              value-format="YYYY-MM-DD" /></el-form-item></template
        ><el-form-item v-if="dialog !== 'complete'" label="原因" required
          ><el-input v-model="action.reason" type="textarea" /></el-form-item
      ></el-form>
      <template #footer
        ><el-button @click="dialog = ''">取消</el-button
        ><el-button type="primary" :loading="saving" @click="save"
          >确认</el-button
        ></template
      ></el-dialog
    >
  </div>
</template>
<style scoped>
.task-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  background: #fff;
  border: 1px solid #dde3df;
  margin-bottom: 16px;
}
.task-stats > div {
  padding: 18px;
  display: grid;
  gap: 5px;
  border-right: 1px solid #e4e8e6;
}
.task-stats span,
.cell-sub {
  color: #75817d;
}
.task-stats strong {
  font-size: 25px;
}
.task-stats .danger strong {
  color: #b54f49;
}
.task-filter {
  grid-template-columns: minmax(180px, 260px);
}
.cell-sub {
  display: block;
  margin-top: 5px;
}
.el-select,
.el-date-editor {
  width: 100%;
}
@media (max-width: 640px) {
  .task-stats {
    grid-template-columns: 1fr 1fr 1fr;
  }
  .task-stats > div {
    padding: 13px;
  }
  .task-stats strong {
    font-size: 20px;
  }
}
</style>
