<template>
  <div>
    <h2 style="margin-top: 0">模型训练与预测</h2>

    <el-row :gutter="20">
      <el-col :span="12">
        <el-card>
          <template #header>训练配置</template>
          <el-form label-width="120px">
            <el-form-item label="模型类型">
              <el-select v-model="trainForm.modelType" style="width: 100%">
                <el-option value="ttranse" label="TTransE (时序TransE扩展)" />
                <el-option value="gru" label="GRU (时序演化模型)" />
              </el-select>
            </el-form-item>
            <el-form-item label="嵌入维度">
              <el-input-number v-model="trainForm.embeddingDim" :min="50" :max="500" />
            </el-form-item>
            <el-form-item label="学习率">
              <el-input-number v-model="trainForm.learningRate" :min="0.0001" :max="1" :step="0.001" :precision="4" />
            </el-form-item>
            <el-form-item label="训练轮次">
              <el-input-number v-model="trainForm.epochs" :min="1" :max="500" />
            </el-form-item>
            <el-form-item label="Batch大小">
              <el-input-number v-model="trainForm.batchSize" :min="64" :max="4096" :step="64" />
            </el-form-item>
            <el-form-item v-if="trainForm.modelType === 'ttranse'" label="Margin">
              <el-input-number v-model="trainForm.margin" :min="0.1" :max="5" :step="0.1" :precision="1" />
            </el-form-item>
            <el-form-item v-if="trainForm.modelType === 'gru'" label="隐藏维度">
              <el-input-number v-model="trainForm.hiddenDim" :min="50" :max="500" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="startTraining" :loading="training">开始训练</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card>
          <template #header>事件预测</template>
          <el-form label-width="120px">
            <el-form-item label="模型类型">
              <el-select v-model="predictForm.modelType" style="width: 100%">
                <el-option value="ttranse" label="TTransE" />
                <el-option value="gru" label="GRU" />
              </el-select>
            </el-form-item>
            <el-form-item label="主体">
              <el-input v-model="predictForm.subject" placeholder="如: 苹果公司" />
            </el-form-item>
            <el-form-item label="关系">
              <el-input v-model="predictForm.relation" placeholder="如: 投资" />
            </el-form-item>
            <el-form-item label="预测时间">
              <el-input v-model="predictForm.timestamp" placeholder="如: 2025-01-01T00:00:00Z" />
            </el-form-item>
            <el-form-item label="Top-K">
              <el-input-number v-model="predictForm.topK" :min="1" :max="50" />
            </el-form-item>
            <el-form-item>
              <el-button type="success" @click="doPrediction" :loading="predicting">预测</el-button>
            </el-form-item>
          </el-form>

          <div v-if="predictions.length > 0" style="margin-top: 16px">
            <h4>预测结果</h4>
            <el-table :data="predictions" stripe size="small">
              <el-table-column prop="rank" label="排名" width="80" />
              <el-table-column prop="entity" label="预测实体" />
              <el-table-column prop="score" label="得分">
                <template #default="{ row }">
                  {{ row.score?.toFixed?.(4) || row.score }}
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card style="margin-top: 20px">
      <template #header>训练任务</template>
      <el-table :data="jobs" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="jobType" label="类型" width="120" />
        <el-table-column prop="modelType" label="模型" width="120" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="jobStatusType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" />
        <el-table-column prop="completedAt" label="完成时间" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button size="small" @click="checkJobStatus(row.id)">刷新</el-button>
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

const training = ref(false)
const predicting = ref(false)
const predictions = ref([])
const jobs = ref([])

const trainForm = ref({
  modelType: 'ttranse',
  embeddingDim: 100,
  learningRate: 0.01,
  epochs: 100,
  batchSize: 512,
  margin: 1.0,
  hiddenDim: 200
})

const predictForm = ref({
  modelType: 'ttranse',
  subject: '',
  relation: '',
  timestamp: '',
  topK: 10
})

async function startTraining() {
  training.value = true
  try {
    const params = {
      embedding_dim: trainForm.value.embeddingDim,
      learning_rate: trainForm.value.learningRate,
      epochs: trainForm.value.epochs,
      batch_size: trainForm.value.batchSize
    }
    if (trainForm.value.modelType === 'ttranse') {
      params.margin = trainForm.value.margin
    } else {
      params.hidden_dim = trainForm.value.hiddenDim
    }
    const { data } = await api.ml.train(trainForm.value.modelType, params)
    ElMessage.success(`训练任务已提交, ID: ${data.id}`)
    loadJobs()
  } catch (e) { ElMessage.error('训练提交失败: ' + e.message) }
  finally { training.value = false }
}

async function doPrediction() {
  predicting.value = true
  try {
    const { data } = await api.ml.predict({
      modelType: predictForm.value.modelType,
      subject: predictForm.value.subject,
      relation: predictForm.value.relation,
      timestamp: predictForm.value.timestamp,
      topK: predictForm.value.topK
    })
    predictions.value = data.predictions || []
    if (data.metadata?.error) ElMessage.warning(data.metadata.error)
  } catch (e) { ElMessage.error('预测失败: ' + e.message) }
  finally { predicting.value = false }
}

function jobStatusType(status) {
  const map = { PENDING: 'info', RUNNING: 'warning', COMPLETED: 'success', FAILED: 'danger' }
  return map[status] || 'info'
}

async function checkJobStatus(id) {
  const { data } = await api.ml.getJob(id)
  if (data) {
    const idx = jobs.value.findIndex(j => j.id === id)
    if (idx >= 0) jobs.value[idx] = data
  }
}

async function loadJobs() {
  try {
    const { data } = await api.ml.listJobs()
    jobs.value = (data || []).reverse()
  } catch (e) { console.error(e) }
}

onMounted(loadJobs)
</script>
