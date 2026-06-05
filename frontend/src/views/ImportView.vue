<template>
  <div>
    <h2 style="margin-top: 0">事件数据导入</h2>
    <el-card>
      <el-tabs v-model="activeTab">
        <el-tab-pane label="CSV导入" name="csv">
          <el-upload
            ref="csvUpload"
            :auto-upload="false"
            :limit="1"
            accept=".csv"
            :on-change="onCsvChange"
          >
            <template #trigger>
              <el-button type="primary">选择CSV文件</el-button>
            </template>
            <el-button class="ml-3" type="success" @click="uploadCsv" style="margin-left: 12px">
              开始导入
            </el-button>
            <template #tip>
              <div style="color: #909399; margin-top: 8px">
                CSV格式要求: 每行一条事件, 必须包含 subject, relation, object, timestamp 列; 可选列: confidence, source, subject_type, object_type, relation_category, time_end
              </div>
            </template>
          </el-upload>
        </el-tab-pane>

        <el-tab-pane label="JSON导入" name="json">
          <el-upload
            ref="jsonUpload"
            :auto-upload="false"
            :limit="1"
            accept=".json"
            :on-change="onJsonChange"
          >
            <template #trigger>
              <el-button type="primary">选择JSON文件</el-button>
            </template>
            <el-button class="ml-3" type="success" @click="uploadJson" style="margin-left: 12px">
              开始导入
            </el-button>
            <template #tip>
              <div style="color: #909399; margin-top: 8px">
                JSON格式支持: 数组格式 [{subject, relation, object, timestamp, ...}] 或嵌套格式 {subjects: [{name, relations: [...]}]}
              </div>
            </template>
          </el-upload>
        </el-tab-pane>

        <el-tab-pane label="API批量导入" name="api">
          <el-input
            v-model="apiInput"
            type="textarea"
            :rows="10"
            placeholder='输入JSON数组, 例如: [{"subject":"苹果公司","relation":"投资","object":"初创公司A","timestamp":"2024-01-15T10:00:00Z"}]'
          />
          <el-button type="success" @click="importViaApi" style="margin-top: 12px" :loading="importing">
            批量导入
          </el-button>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-card style="margin-top: 20px">
      <template #header>导入任务列表</template>
      <el-table :data="importJobs" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="format" label="格式" width="100" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalRecords" label="总记录" width="100" />
        <el-table-column prop="processedRecords" label="已处理" width="100" />
        <el-table-column prop="failedRecords" label="失败" width="80" />
        <el-table-column prop="duplicatesSkipped" label="跳过重复" width="100" />
        <el-table-column prop="createdAt" label="创建时间" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const activeTab = ref('csv')
const csvFile = ref(null)
const jsonFile = ref(null)
const apiInput = ref('')
const importing = ref(false)
const importJobs = ref([])

function onCsvChange(file) { csvFile.value = file.raw }
function onJsonChange(file) { jsonFile.value = file.raw }

async function uploadCsv() {
  if (!csvFile.value) { ElMessage.warning('请先选择CSV文件'); return }
  importing.value = true
  try {
    const { data } = await api.import.uploadCsv(csvFile.value)
    ElMessage.success(`导入任务已提交, ID: ${data.id}`)
    loadJobs()
  } catch (e) { ElMessage.error('导入失败: ' + e.message) }
  finally { importing.value = false }
}

async function uploadJson() {
  if (!jsonFile.value) { ElMessage.warning('请先选择JSON文件'); return }
  importing.value = true
  try {
    const { data } = await api.import.uploadJson(jsonFile.value)
    ElMessage.success(`导入任务已提交, ID: ${data.id}`)
    loadJobs()
  } catch (e) { ElMessage.error('导入失败: ' + e.message) }
  finally { importing.value = false }
}

async function importViaApi() {
  if (!apiInput.value.trim()) { ElMessage.warning('请输入数据'); return }
  importing.value = true
  try {
    const events = JSON.parse(apiInput.value)
    const { data } = await api.import.importEvents(events)
    ElMessage.success(`导入完成, 处理: ${data.processedRecords}, 失败: ${data.failedRecords}`)
    loadJobs()
  } catch (e) { ElMessage.error('导入失败: ' + e.message) }
  finally { importing.value = false }
}

function statusType(status) {
  const map = { PENDING: 'info', RUNNING: 'warning', COMPLETED: 'success', FAILED: 'danger' }
  return map[status] || 'info'
}

async function loadJobs() {
  try {
    const { data } = await api.import.listJobs()
    importJobs.value = (data || []).reverse()
  } catch (e) { console.error(e) }
}

onMounted(loadJobs)
</script>
