<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import {
  ArrowRight,
  CircleCheck,
  Plus,
  Warning,
} from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { getDashboardOverview, type DashboardOverview } from "../api/platform";
import { getTasks, type TaskItem } from "../api/task";
import { getStoredUser } from "../auth/session";

const router = useRouter();
const user = getStoredUser();
const loading = ref(false);
const overview = ref<DashboardOverview | null>(null);
const tasks = ref<TaskItem[]>([]);
const canCreateCattle = computed(
  () =>
    user?.roles.some((role) => ["ADMIN", "FARM_MANAGER"].includes(role)) ??
    false,
);
const stats = computed(() => [
  {
    label: "在场牛只",
    value: overview.value?.inField ?? "--",
    detail: "当前在场档案",
    tone: "green",
    target: { path: "/cattle", query: { presenceStatus: "IN_FIELD" } },
  },
  {
    label: "待处理任务",
    value: overview.value?.pendingTasks ?? "--",
    detail: `其中逾期 ${overview.value?.overdueTasks ?? "--"} 项`,
    tone: "amber",
    target: { path: "/tasks", query: { status: "PENDING" } },
  },
  {
    label: "健康异常",
    value: overview.value?.healthAlerts ?? "--",
    detail: `休药期限制 ${overview.value?.withdrawalRestricted ?? "--"} 头`,
    tone: "red",
    target: { path: "/health", query: { status: "PROCESSING" } },
  },
  {
    label: "妊娠母牛",
    value: overview.value?.pregnantCows ?? "--",
    detail: `近期预产 ${overview.value?.dueSoon ?? "--"} 头`,
    tone: "blue",
    target: { path: "/breeding" },
  },
]);
const priorityLabels: Record<string, string> = {
  NORMAL: "普通",
  IMPORTANT: "重要",
  URGENT: "紧急",
};
const statusLabels: Record<string, string> = {
  PENDING: "待处理",
  IN_PROGRESS: "进行中",
  OVERDUE: "已逾期",
  DONE: "已完成",
};

async function load() {
  loading.value = true;
  try {
    const [summary, pending, overdue] = await Promise.all([
      getDashboardOverview(),
      getTasks("PENDING"),
      getTasks("OVERDUE"),
    ]);
    overview.value = summary;
    tasks.value = [...overdue, ...pending].slice(0, 6);
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || "工作台数据加载失败");
  } finally {
    loading.value = false;
  }
}
onMounted(load);
</script>

<template>
  <div class="dashboard" v-loading="loading">
    <div class="page-heading">
      <div>
        <p class="eyebrow">牛场经营概览</p>
        <h1>工作台</h1>
      </div>
      <el-button
        v-if="canCreateCattle"
        type="primary"
        :icon="Plus"
        @click="router.push('/cattle')"
        >牛只建档</el-button
      >
    </div>
    <section class="stats-grid">
      <article
        v-for="item in stats"
        :key="item.label"
        class="stat-item"
        :class="item.tone"
        role="button"
        tabindex="0"
        @click="router.push(item.target)"
        @keyup.enter="router.push(item.target)"
      >
        <span>{{ item.label }}</span
        ><strong>{{ item.value }}</strong
        ><small>{{ item.detail }}</small>
      </article>
    </section>
    <section class="workspace-grid">
      <div class="data-panel task-panel">
        <div class="panel-heading">
          <div>
            <h2>重点任务</h2>
            <p>逾期任务优先显示</p>
          </div>
          <button @click="router.push('/tasks')">
            查看全部 <el-icon><ArrowRight /></el-icon>
          </button>
        </div>
        <el-empty
          v-if="!tasks.length"
          description="当前没有待处理任务"
          :image-size="72"
        />
        <div v-else class="task-table">
          <div v-for="task in tasks" :key="task.taskId" class="task-row">
            <div class="task-kind">
              <span class="task-icon"
                ><el-icon><CircleCheck /></el-icon></span
              ><span
                ><strong>{{ task.title }}</strong
                ><small>{{
                  task.earTagNo || task.assigneeName || "全场任务"
                }}</small></span
              >
            </div>
            <span>{{ priorityLabels[task.priority] || task.priority }}</span
            ><span>{{ task.dueDate }}</span>
            <el-tag
              :type="task.status === 'OVERDUE' ? 'danger' : 'warning'"
              effect="plain"
              >{{ statusLabels[task.status] || task.status }}</el-tag
            >
          </div>
        </div>
      </div>
      <aside class="data-panel alerts-panel">
        <div class="panel-heading">
          <div>
            <h2>现场提醒</h2>
            <p>需要优先关注的经营指标</p>
          </div>
        </div>
        <div
          class="alert-item"
          role="button"
          tabindex="0"
          @click="
            router.push({ path: '/health', query: { status: 'PROCESSING' } })
          "
        >
          <el-icon><Warning /></el-icon
          ><span
            ><strong
              >{{ overview?.healthAlerts ?? "--" }} 头牛健康状态异常</strong
            ><small>进入健康诊疗查看详情</small></span
          >
        </div>
        <div
          class="alert-item"
          role="button"
          tabindex="0"
          @click="router.push({ path: '/tasks', query: { status: 'OVERDUE' } })"
        >
          <el-icon><Warning /></el-icon
          ><span
            ><strong>{{ overview?.overdueTasks ?? "--" }} 项任务已逾期</strong
            ><small>请及时重新分派或处理</small></span
          >
        </div>
        <div
          class="alert-item"
          role="button"
          tabindex="0"
          @click="router.push({ path: '/feeding', query: { tab: 'orders' } })"
        >
          <el-icon><Warning /></el-icon
          ><span
            ><strong
              >今日
              {{ overview?.todayMixingOrders ?? "--" }} 张配料单待执行</strong
            ><small>进入配料管理核对执行情况</small></span
          >
        </div>
      </aside>
    </section>
  </div>
</template>
