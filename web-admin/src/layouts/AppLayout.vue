<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  Bell,
  CircleCheck,
  DataAnalysis,
  DocumentChecked,
  FirstAidKit,
  Food,
  House,
  List,
  Menu,
  Operation,
  PieChart,
  Setting,
  SwitchButton,
  TrendCharts,
  Upload,
  User,
} from "@element-plus/icons-vue";
import { logout } from "../api/auth";
import { getFarm } from "../api/platform";
import { clearSession, getStoredUser } from "../auth/session";

const route = useRoute();
const router = useRouter();
const drawerOpen = ref(false);
const user = getStoredUser();
const farmName = ref("当前牛场");
const operatorName = computed(
  () => user?.realName || user?.username || "牛场员工",
);
const roleLabels: Record<string, string> = {
  ADMIN: "管理员",
  FARM_MANAGER: "场长",
  VET: "兽医",
  BREEDER: "繁育员",
  WORKER: "饲养员",
};
const roleName = computed(
  () =>
    user?.roles.map((role) => roleLabels[role] || role).join(" / ") || "员工",
);
type NavItem = { label: string; icon: unknown; path: string; roles?: string[] };
const allNavItems: NavItem[] = [
  { label: "工作台", icon: DataAnalysis, path: "/" },
  { label: "牛只档案", icon: List, path: "/cattle" },
  {
    label: "批量导入",
    icon: Upload,
    path: "/cattle/import",
    roles: ["ADMIN", "FARM_MANAGER"],
  },
  { label: "变动记录", icon: SwitchButton, path: "/movements" },
  { label: "栏舍牛群", icon: House, path: "/locations" },
  { label: "任务中心", icon: Operation, path: "/tasks" },
  {
    label: "繁育管理",
    icon: User,
    path: "/breeding",
    roles: ["ADMIN", "FARM_MANAGER", "VET", "BREEDER", "WORKER"],
  },
  {
    label: "健康诊疗",
    icon: FirstAidKit,
    path: "/health",
    roles: ["ADMIN", "FARM_MANAGER", "VET", "BREEDER", "WORKER"],
  },
  { label: "防疫管理", icon: CircleCheck, path: "/vaccinations" },
  { label: "生长管理", icon: TrendCharts, path: "/growth" },
  { label: "配料管理", icon: Food, path: "/feeding" },
  {
    label: "报表中心",
    icon: PieChart,
    path: "/reports",
    roles: ["ADMIN", "FARM_MANAGER", "VET", "BREEDER"],
  },
  {
    label: "审计中心",
    icon: DocumentChecked,
    path: "/audit",
    roles: ["ADMIN", "FARM_MANAGER"],
  },
  {
    label: "系统配置",
    icon: Setting,
    path: "/settings",
    roles: ["ADMIN", "FARM_MANAGER"],
  },
  {
    label: "用户权限",
    icon: User,
    path: "/settings/users",
    roles: ["ADMIN", "FARM_MANAGER"],
  },
];
const navItems = computed(() =>
  allNavItems.filter(
    (item) =>
      !item.roles || item.roles.some((role) => user?.roles.includes(role)),
  ),
);

function navigate(path: string) {
  drawerOpen.value = false;
  router.push(path);
}

async function signOut() {
  try {
    await logout();
  } finally {
    clearSession();
    await router.replace("/login");
  }
}

onMounted(async () => {
  try {
    farmName.value = (await getFarm()).farmName;
  } catch {
    /* Keep the shell usable when profile loading fails. */
  }
});
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <span class="brand-mark">牛</span><strong>牧衡</strong>
      </div>
      <nav>
        <button
          v-for="item in navItems"
          :key="item.path"
          class="nav-item"
          :class="{ active: route.path === item.path }"
          @click="navigate(item.path)"
        >
          <el-icon><component :is="item.icon" /></el-icon
          ><span>{{ item.label }}</span>
        </button>
      </nav>
      <div class="farm-switch">
        <span>当前牛场</span><strong>{{ farmName }}</strong>
      </div>
    </aside>
    <main class="main-area">
      <header class="topbar">
        <button
          class="icon-button mobile-menu"
          title="打开菜单"
          @click="drawerOpen = true"
        >
          <el-icon><Menu /></el-icon>
        </button>
        <div class="topbar-context">
          <span>{{ farmName }}</span
          ><small>牛场全生命周期管理</small>
        </div>
        <div class="top-actions">
          <button class="icon-button" title="通知">
            <el-icon><Bell /></el-icon>
          </button>
          <el-dropdown trigger="click">
            <div class="operator">
              <span class="avatar">{{ operatorName.slice(0, 1) }}</span
              ><span
                ><strong>{{ operatorName }}</strong
                ><small>{{ roleName }}</small></span
              >
            </div>
            <template #dropdown
              ><el-dropdown-menu
                ><el-dropdown-item :icon="SwitchButton" @click="signOut"
                  >退出登录</el-dropdown-item
                ></el-dropdown-menu
              ></template
            >
          </el-dropdown>
        </div>
      </header>
      <section class="content"><router-view /></section>
    </main>
    <el-drawer
      v-model="drawerOpen"
      direction="ltr"
      size="260px"
      :with-header="false"
      class="mobile-drawer"
    >
      <div class="drawer-brand">
        <span class="brand-mark">牛</span><strong>牧衡</strong>
      </div>
      <button
        v-for="item in navItems"
        :key="item.path"
        class="drawer-nav"
        @click="navigate(item.path)"
      >
        <el-icon><component :is="item.icon" /></el-icon>{{ item.label }}
      </button>
    </el-drawer>
  </div>
</template>
