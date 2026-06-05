<template>
  <div>
    <h2 style="margin-top: 0">事件预警</h2>

    <el-card>
      <template #header>
        活跃预警
        <el-button type="primary" size="small" @click="loadAlerts" style="float: right">刷新</el-button>
      </template>

      <el-table :data="alerts" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="patternId" label="触发模式ID" width="120" />
        <el-table-column label="预测事件" min-width="300">
          <template #default="{ row }">
            <code>{{ formatEvent(row.predictedEvent) }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="predictedTimeWindow" label="预测时间窗口" width="200" />
        <el-table-column prop="confidence" label="置信度" width="120">
          <template #default="{ row }">
            <el-tag :type="row.confidence > 0.8 ? 'danger' : row.confidence > 0.5 ? 'warning' : 'info'">
              {{ (row.confidence * 100).toFixed(1) }}%
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="生成时间" width="200" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button size="small" type="danger" @click="dismissAlert(row.id)">忽略</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card style="margin-top: 20px">
      <template #header>流式事件接入</template>
      <el-form label-width="100px">
        <el-form-item label="主体">
          <el-input v-model="streamEvent.subject" placeholder="事件主体" />
        </el-form-item>
        <el-form-item label="关系">
          <el-input v-model="streamEvent.relation" placeholder="关系" />
        </el-form-item>
        <el-form-item label="客体">
          <el-input v-model="streamEvent.object" placeholder="事件客体" />
        </el-form-item>
        <el-form-item label="时间戳">
          <el-input v-model="streamEvent.timestamp" placeholder="如: 2024-06-01T10:00:00Z" />
        </el-form-item>
        <el-form-item label="置信度">
          <el-input-number v-model="streamEvent.confidence" :min="0" :max="1" :step="0.1" :precision="1" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="submitEvent">提交事件</el-button>
          <span style="color: #909399; margin-left: 12px">新事件将自动触发模式匹配和预警</span>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const alerts = ref([])
const streamEvent = ref({
  subject: '',
  relation: '',
  object: '',
  timestamp: new Date().toISOString(),
  confidence: 1.0
})

function formatEvent(jsonStr) {
  try { return JSON.stringify(JSON.parse(jsonStr), null, 2) } catch { return jsonStr }
}

async function loadAlerts() {
  try {
    const { data } = await api.ml.getAlerts()
    alerts.value = data || []
  } catch (e) { console.error(e) }
}

async function dismissAlert(id) {
  await api.ml.dismissAlert(id)
  alerts.value = alerts.value.filter(a => a.id !== id)
}

async function submitEvent() {
  try {
    const { data } = await api.ml.ingestEvent(streamEvent.value)
    ElMessage.success(data.created ? '事件已录入并触发模式检测' : '事件已存在(重复跳过)')
    loadAlerts()
  } catch (e) { ElMessage.error('提交失败: ' + e.message) }
}

onMounted(loadAlerts)
</script>
