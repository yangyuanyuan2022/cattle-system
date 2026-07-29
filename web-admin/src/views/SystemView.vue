<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Edit, Plus, Refresh } from '@element-plus/icons-vue'
import { createDictionaryItem, getBusinessRules, getDictionaryItems, getDictionaryTypes, getFarm, updateBusinessRules, updateDictionaryItem, updateFarm, type BusinessRule, type DictionaryItem, type DictionaryType, type FarmInfo } from '../api/platform'
import { getStoredUser } from '../auth/session'

const user = getStoredUser()
const isAdmin = computed(() => user?.roles.includes('ADMIN') ?? false)
const tab = ref('farm')
const loading = ref(false)
const saving = ref(false)
const farm = ref<FarmInfo | null>(null)
const farmForm = reactive({ farmName: '', contactName: '', contactPhone: '', remark: '' })
const rules = ref<BusinessRule[]>([])
const ruleValues = reactive<Record<string, string>>({})
const types = ref<DictionaryType[]>([])
const selectedType = ref('')
const items = ref<DictionaryItem[]>([])
const itemDialog = ref(false)
const editingItem = ref<DictionaryItem | null>(null)
const itemForm = reactive({ itemCode: '', itemName: '', sortNo: 0, status: 'ENABLED', remark: '' })

async function loadBase() {
  loading.value = true
  try {
    const requests: Promise<unknown>[] = [getFarm(), getDictionaryTypes()]
    if (user?.roles.some((role) => ['ADMIN', 'FARM_MANAGER'].includes(role))) requests.push(getBusinessRules())
    const [farmData, typeData, ruleData] = await Promise.all(requests)
    farm.value = farmData as FarmInfo
    Object.assign(farmForm, { farmName: farm.value.farmName, contactName: farm.value.contactName || '', contactPhone: farm.value.contactPhone || '', remark: farm.value.remark || '' })
    types.value = typeData as DictionaryType[]
    if (!selectedType.value && types.value.length) selectedType.value = types.value[0].typeCode
    rules.value = (ruleData as BusinessRule[] | undefined) || []
    rules.value.forEach((rule) => { ruleValues[rule.code] = rule.value })
  } catch (error: any) { ElMessage.error(error.response?.data?.message || '系统配置加载失败') }
  finally { loading.value = false }
}

async function loadItems() {
  if (!selectedType.value) return
  try { items.value = await getDictionaryItems(selectedType.value) }
  catch (error: any) { ElMessage.error(error.response?.data?.message || '字典数据加载失败') }
}

async function saveFarm() {
  if (!farm.value || !farmForm.farmName.trim()) return ElMessage.warning('请填写牛场名称')
  saving.value = true
  try {
    farm.value = await updateFarm({ ...farmForm, version: farm.value.version })
    ElMessage.success('牛场资料已保存')
  } catch (error: any) { ElMessage.error(error.response?.data?.message || '保存失败') }
  finally { saving.value = false }
}

async function saveRules() {
  saving.value = true
  try { rules.value = await updateBusinessRules({ ...ruleValues }); ElMessage.success('业务参数已保存') }
  catch (error: any) { ElMessage.error(error.response?.data?.message || '保存失败') }
  finally { saving.value = false }
}

function openItem(item?: DictionaryItem) {
  editingItem.value = item || null
  Object.assign(itemForm, item ? { itemCode: item.itemCode, itemName: item.itemName, sortNo: item.sortNo, status: item.status, remark: item.remark || '' } : { itemCode: '', itemName: '', sortNo: items.value.length * 10 + 10, status: 'ENABLED', remark: '' })
  itemDialog.value = true
}

async function saveItem() {
  if (!itemForm.itemName.trim() || (!editingItem.value && !itemForm.itemCode.trim())) return ElMessage.warning('请完整填写字典项')
  saving.value = true
  try {
    if (editingItem.value) await updateDictionaryItem(editingItem.value.itemId, { itemName: itemForm.itemName, status: itemForm.status, sortNo: itemForm.sortNo, remark: itemForm.remark })
    else await createDictionaryItem({ typeCode: selectedType.value, itemCode: itemForm.itemCode.toUpperCase(), itemName: itemForm.itemName, sortNo: itemForm.sortNo, remark: itemForm.remark })
    itemDialog.value = false
    await loadItems()
    ElMessage.success('字典项已保存')
  } catch (error: any) { ElMessage.error(error.response?.data?.message || '保存失败') }
  finally { saving.value = false }
}

watch(selectedType, loadItems)
onMounted(async () => { await loadBase(); await loadItems() })
</script>

<template>
  <div>
    <div class="page-heading">
      <div><p class="eyebrow">基础资料与规则</p><h1>系统配置</h1></div>
      <el-button :icon="Refresh" :loading="loading" @click="loadBase">刷新</el-button>
    </div>
    <el-tabs v-model="tab">
      <el-tab-pane label="牛场资料" name="farm" />
      <el-tab-pane v-if="user?.roles.some((r) => ['ADMIN','FARM_MANAGER'].includes(r))" label="业务参数" name="rules" />
      <el-tab-pane label="业务字典" name="dictionary" />
    </el-tabs>

    <section v-if="tab === 'farm'" class="data-panel settings-panel" v-loading="loading">
      <div class="section-title"><div><h2>牛场资料</h2><p>用于系统抬头、报表和现场联系信息</p></div><el-tag effect="plain">{{ farm?.farmCode }}</el-tag></div>
      <el-form label-position="top" class="settings-form">
        <el-form-item label="牛场名称" required><el-input v-model="farmForm.farmName" :disabled="!isAdmin" /></el-form-item>
        <el-form-item label="牛场类型"><el-input :model-value="farm?.farmType" disabled /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="farmForm.contactName" :disabled="!isAdmin" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="farmForm.contactPhone" :disabled="!isAdmin" /></el-form-item>
        <el-form-item label="备注" class="wide"><el-input v-model="farmForm.remark" type="textarea" :rows="3" :disabled="!isAdmin" /></el-form-item>
      </el-form>
      <div v-if="isAdmin" class="form-actions"><el-button type="primary" :loading="saving" @click="saveFarm">保存资料</el-button></div>
    </section>

    <section v-else-if="tab === 'rules'" class="data-panel settings-panel" v-loading="loading">
      <div class="section-title"><div><h2>业务参数</h2><p>参数修改会影响后续业务校验，已发生记录不追溯修改</p></div></div>
      <el-form label-position="top" class="rule-grid">
        <el-form-item v-for="rule in rules" :key="rule.code" :label="rule.name">
          <el-input v-model="ruleValues[rule.code]" :disabled="!isAdmin"><template #append>{{ rule.remark || rule.valueType }}</template></el-input>
        </el-form-item>
      </el-form>
      <div v-if="isAdmin" class="form-actions"><el-button type="primary" :loading="saving" @click="saveRules">保存参数</el-button></div>
    </section>

    <section v-else class="data-panel settings-panel">
      <div class="dictionary-toolbar">
        <el-select v-model="selectedType" filterable aria-label="字典类型"><el-option v-for="type in types" :key="type.typeCode" :label="type.typeName" :value="type.typeCode" /></el-select>
        <el-button v-if="isAdmin" type="primary" :icon="Plus" @click="openItem()">新增字典项</el-button>
      </div>
      <el-table :data="items" v-loading="loading">
        <el-table-column prop="itemCode" label="编码" min-width="150" />
        <el-table-column prop="itemName" label="名称" min-width="180" />
        <el-table-column prop="sortNo" label="排序" width="90" />
        <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'">{{ row.status === 'ENABLED' ? '启用' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column prop="remark" label="备注" min-width="220" show-overflow-tooltip />
        <el-table-column v-if="isAdmin" label="操作" width="90"><template #default="{ row }"><el-button link type="primary" :icon="Edit" @click="openItem(row)">编辑</el-button></template></el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="itemDialog" :title="editingItem ? '编辑字典项' : '新增字典项'" width="min(520px,94vw)">
      <el-form label-position="top">
        <el-form-item label="编码" required><el-input v-model="itemForm.itemCode" :disabled="!!editingItem" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="itemForm.itemName" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="itemForm.sortNo" :min="0" /></el-form-item>
        <el-form-item v-if="editingItem" label="状态"><el-segmented v-model="itemForm.status" :options="[{label:'启用',value:'ENABLED'},{label:'停用',value:'DISABLED'}]" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="itemForm.remark" type="textarea" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="itemDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveItem">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.settings-panel{padding:20px;max-width:1100px}.section-title,.dictionary-toolbar{display:flex;align-items:center;justify-content:space-between;gap:16px;margin-bottom:20px}.section-title h2{margin:0 0 5px}.section-title p{margin:0;color:#6f7c77}.settings-form,.rule-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:0 18px}.wide{grid-column:1/-1}.form-actions{display:flex;justify-content:flex-end;border-top:1px solid #e5eae7;padding-top:16px}.dictionary-toolbar .el-select{width:min(360px,100%)}@media(max-width:700px){.settings-form,.rule-grid{grid-template-columns:1fr}.wide{grid-column:auto}.dictionary-toolbar{align-items:stretch;flex-direction:column}.dictionary-toolbar .el-select{width:100%}}
</style>
