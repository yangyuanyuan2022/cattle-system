<script setup lang="ts">
import { onMounted, ref } from "vue";
import { ElMessage, ElMessageBox, type UploadFile } from "element-plus";
import { Download, Upload } from "@element-plus/icons-vue";
import {
  confirmCattleImport,
  downloadImportTemplate,
  getImportErrors,
  getImportLogs,
  validateCattleImport,
  type ImportError,
  type ImportLog,
  type ImportResult,
} from "../api/operations";
const file = ref<File | null>(null),
  result = ref<ImportResult | null>(null),
  logs = ref<ImportLog[]>([]),
  errors = ref<ImportError[]>([]),
  busy = ref(false);
const statusLabels: Record<string, string> = {
  VALIDATED: "校验完成",
  INVALID: "校验失败",
  CONFIRMED: "已导入",
  FAILED: "失败",
};
async function load() {
  try {
    logs.value = await getImportLogs();
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || "导入记录加载失败");
  }
}
function choose(upload: UploadFile) {
  file.value = upload.raw || null;
  result.value = null;
  errors.value = [];
}
async function validate() {
  if (!file.value) return ElMessage.warning("请先选择 Excel 文件");
  busy.value = true;
  try {
    result.value = await validateCattleImport(file.value);
    errors.value = result.value.errors || [];
    ElMessage.success(
      result.value.failCount
        ? "校验完成，请处理错误行"
        : "校验通过，可以确认导入",
    );
    await load();
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || "文件校验失败");
  } finally {
    busy.value = false;
  }
}
async function confirm() {
  if (!result.value || result.value.failCount) return;
  await ElMessageBox.confirm(
    `确认导入 ${result.value.successCount} 条合法数据？`,
    "确认导入",
    { type: "warning" },
  );
  busy.value = true;
  try {
    result.value = await confirmCattleImport(result.value.importId);
    ElMessage.success(`成功导入 ${result.value.successCount} 条牛只档案`);
    file.value = null;
    await load();
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || "确认导入失败");
  } finally {
    busy.value = false;
  }
}
async function showErrors(row: ImportLog) {
  errors.value = await getImportErrors(row.importId);
}
onMounted(load);
</script>
<template>
  <div>
    <div class="page-heading">
      <div>
        <p class="eyebrow">牛只管理</p>
        <h1>批量导入</h1>
      </div>
      <el-button :icon="Download" @click="downloadImportTemplate"
        >下载模板</el-button
      >
    </div>
    <section class="import-workbench">
      <el-upload
        drag
        accept=".xlsx"
        :auto-upload="false"
        :limit="1"
        :on-change="choose"
        :on-remove="
          () => {
            file = null;
            result = null;
          }
        "
        ><el-icon class="upload-icon"><Upload /></el-icon>
        <div>将填写好的 Excel 拖到此处，或点击选择文件</div>
        <template #tip
          ><span>仅支持系统模板生成的 .xlsx 文件</span></template
        ></el-upload
      >
      <div class="import-actions">
        <el-button :loading="busy" @click="validate">预校验</el-button
        ><el-button
          type="primary"
          :loading="busy"
          :disabled="
            !result || result.failCount > 0 || result.status === 'CONFIRMED'
          "
          @click="confirm"
          >确认导入</el-button
        >
      </div>
      <div v-if="result" class="import-summary">
        <span
          >总行数<strong>{{ result.totalCount }}</strong></span
        ><span
          >合法<strong>{{ result.successCount }}</strong></span
        ><span :class="{ danger: result.failCount }"
          >错误<strong>{{ result.failCount }}</strong></span
        ><el-tag effect="plain">{{
          statusLabels[result.status] || result.status
        }}</el-tag>
      </div>
    </section>
    <section v-if="errors.length" class="table-panel">
      <div class="panel-heading">
        <div>
          <h2>错误行明细</h2>
          <p>修改源文件后重新上传校验</p>
        </div>
      </div>
      <el-table :data="errors"
        ><el-table-column
          prop="rowNo"
          label="行号"
          width="70" /><el-table-column
          prop="fieldName"
          label="字段"
          width="130" /><el-table-column
          prop="rawValue"
          label="原值"
          min-width="140" /><el-table-column
          prop="errorMessage"
          label="错误原因"
          min-width="240"
      /></el-table>
    </section>
    <section class="table-panel">
      <div class="panel-heading">
        <div>
          <h2>导入记录</h2>
          <p>保留每次校验与确认结果</p>
        </div>
      </div>
      <el-table :data="logs" empty-text="暂无导入记录"
        ><el-table-column
          prop="fileName"
          label="文件"
          min-width="180"
        /><el-table-column
          prop="createdAt"
          label="时间"
          min-width="170"
        /><el-table-column label="结果" min-width="180"
          ><template #default="s"
            >{{ s.row.successCount }} 成功 /
            {{ s.row.failCount }} 失败</template
          ></el-table-column
        ><el-table-column label="状态" width="110"
          ><template #default="s"
            ><el-tag effect="plain">{{
              statusLabels[s.row.status] || s.row.status
            }}</el-tag></template
          ></el-table-column
        ><el-table-column label="操作" width="90"
          ><template #default="s"
            ><el-button
              v-if="s.row.failCount"
              link
              type="primary"
              @click="showErrors(s.row)"
              >错误明细</el-button
            ></template
          ></el-table-column
        ></el-table
      >
    </section>
  </div>
</template>
<style scoped>
.import-workbench {
  background: #fff;
  border: 1px solid #dde3df;
  padding: 20px;
  margin-bottom: 16px;
}
.upload-icon {
  font-size: 34px;
  color: #238060;
}
.import-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 16px;
}
.import-summary {
  display: flex;
  align-items: center;
  gap: 24px;
  border-top: 1px solid #e4e8e6;
  margin-top: 16px;
  padding-top: 16px;
}
.import-summary span {
  color: #75817d;
}
.import-summary strong {
  display: block;
  font-size: 22px;
  color: #26332e;
  margin-top: 3px;
}
.import-summary .danger strong {
  color: #b54f49;
}
@media (max-width: 640px) {
  .import-summary {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 10px;
  }
  .import-summary .el-tag {
    grid-column: 1/-1;
  }
  .import-actions .el-button {
    flex: 1;
  }
}
</style>
