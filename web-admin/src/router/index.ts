import { createRouter, createWebHistory } from "vue-router";
import AppLayout from "../layouts/AppLayout.vue";
import { getStoredUser, getToken } from "../auth/session";

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: "/login",
      name: "login",
      component: () => import("../views/LoginView.vue"),
      meta: { public: true },
    },
    {
      path: "/",
      component: AppLayout,
      children: [
        {
          path: "",
          name: "dashboard",
          component: () => import("../views/DashboardView.vue"),
        },
        {
          path: "cattle",
          name: "cattle",
          component: () => import("../views/CattleListView.vue"),
        },
        {
          path: "cattle/import",
          name: "cattle-import",
          component: () => import("../views/CattleImportView.vue"),
          meta: { roles: ["ADMIN", "FARM_MANAGER"] },
        },
        {
          path: "movements",
          name: "movements",
          component: () => import("../views/MovementView.vue"),
        },
        {
          path: "locations",
          name: "locations",
          component: () => import("../views/LocationView.vue"),
        },
        {
          path: "health",
          name: "health",
          component: () => import("../views/HealthView.vue"),
          meta: {
            roles: ["ADMIN", "FARM_MANAGER", "VET", "BREEDER", "WORKER"],
          },
        },
        {
          path: "vaccinations",
          name: "vaccinations",
          component: () => import("../views/VaccinationView.vue"),
          meta: { roles: ["ADMIN", "FARM_MANAGER", "VET", "WORKER"] },
        },
        {
          path: "growth",
          name: "growth",
          component: () => import("../views/GrowthView.vue"),
        },
        {
          path: "breeding",
          name: "breeding",
          component: () => import("../views/BreedingView.vue"),
          meta: {
            roles: ["ADMIN", "FARM_MANAGER", "VET", "BREEDER", "WORKER"],
          },
        },
        {
          path: "tasks",
          name: "tasks",
          component: () => import("../views/TaskView.vue"),
        },
        {
          path: "feeding",
          name: "feeding",
          component: () => import("../views/FeedingView.vue"),
        },
        {
          path: "reports",
          name: "reports",
          component: () => import("../views/ReportView.vue"),
          meta: { roles: ["ADMIN", "FARM_MANAGER", "VET", "BREEDER"] },
        },
        {
          path: "audit",
          name: "audit",
          component: () => import("../views/AuditView.vue"),
          meta: { roles: ["ADMIN", "FARM_MANAGER"] },
        },
        {
          path: "settings",
          name: "settings",
          component: () => import("../views/SystemView.vue"),
          meta: { roles: ["ADMIN", "FARM_MANAGER"] },
        },
        {
          path: "settings/users",
          name: "users",
          component: () => import("../views/UserView.vue"),
          meta: { roles: ["ADMIN", "FARM_MANAGER"] },
        },
      ],
    },
    { path: "/:pathMatch(.*)*", redirect: "/" },
  ],
});

router.beforeEach((to) => {
  const token = getToken();
  if (!to.meta.public && !token)
    return { name: "login", query: { redirect: to.fullPath } };
  if (to.name === "login" && token) return { name: "dashboard" };
  const allowed = to.meta.roles as string[] | undefined;
  const roles = getStoredUser()?.roles || [];
  if (allowed?.length && !allowed.some((role) => roles.includes(role)))
    return { name: "dashboard" };
});

export default router;
