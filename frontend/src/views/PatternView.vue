<template>
  <div>
    <h2 style="margin-top: 0">时序模式挖掘</h2>

    <el-card style="margin-bottom: 20px">
      <template #header>挖掘参数配置</template>
      <el-row :gutter="16">
        <el-col :span="5">
          <el-form-item label="最小支持度">
            <el-input-number v-model="mineParams.minSupport" :min="0" :max="1" :step="0.01" :precision="2" />
          </el-form-item>
        </el-col>
        <el-col :span="5">
          <el-form-item label="最小置信度">
            <el-input-number v-model="mineParams.minConfidence" :min="0" :max="1" :step="0.1" :precision="1" />
          </el-form-item>
        </el-col>
        <el-col :span="5">
          <el-form-item label="最大时间间隔(小时)">
            <el-input-number v-model="mineParams.maxTimeGapHours" :min="1" :max="8760" />
          </el-form-item>
        </el-col>
        <el-col :span="4">
          <el-button type="primary" @click="startMining" :loading="mining">开始挖掘</el-button>
        </el-col>
      </el-row>
    </el-card>

    <el-card>
      <template #header>
        发现的时序模式
        <el-tag style="margin-left: 8px">{{ patterns.length }} 条</el-tag>
      </template>

      <el-table :data="patterns" stripe max-height="600">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="patternType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.patternType === 'CHAIN' ? 'warning' : 'success'" size="small">
              {{ row.patternType === 'CHAIN' ? '事件链' : '事件对' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="前件" min-width="200">
          <template #default="{ row }">
            <code>{{ formatPattern(row.antecedent) }}</code>
          </template>
        </el-table-column>
        <el-table-column label="→" width="40" />
        <el-table-column label="后件" min-width="200">
          <template #default="{ row }">
            <code>{{ formatPattern(row.consequent) }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="support" label="支持度" width="100">
          <template #default="{ row }">
            {{ (row.support * 100).toFixed(2) }}%
          </template>
        </el-table-column>
        <el-table-column prop="confidence" label="置信度" width="100">
          <template #default="{ row }">
            <el-tag :type="row.confidence > 0.8 ? 'danger' : row.confidence > 0.5 ? 'warning' : 'info'" size="small">
              {{ (row.confidence * 100).toFixed(1) }}%
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="avgTimeIntervalHours" label="平均时间间隔" width="140">
          <template #default="{ row }">
            {{ row.avgTimeIntervalHours ? row.avgTimeIntervalHours.toFixed(1) + '小时' : '-' }}
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const mining = ref(false)
const patterns = ref([])
const mineParams = ref({
  minSupport: 0.01,
  minConfidence: 0.5,
  maxTimeGapHours: 720
})

function formatPattern(jsonStr) {
  try {
    const obj = JSON.parse(jsonStr)
    return obj.relation || JSON.stringify(obj)
  } catch { return jsonStr }
}

async function startMining() {
  mining.value = true
  try {
    const { data } = await api.ml.minePatterns(mineParams.value)
    ElMessage.success(`挖掘任务已提交, ID: ${data.id}`)
    setTimeout(loadPatterns, 3000)
  } catch (e) { ElMessage.error('挖掘失败: ' + e.message) }
  finally { mining.value = false }
}

async function loadPatterns() {
  try {
    const { data } = await api.patterns.list({ minSupport: mineParams.value.minSupport, minConfidence: mineParams.value.minConfidence })
    patterns.value = data || []
  } catch (e) { console.error(e) }
}

onMounted(loadPatterns)
</script>
