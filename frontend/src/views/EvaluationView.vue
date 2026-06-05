<template>
  <div>
    <h2 style="margin-top: 0">评估报告</h2>

    <el-card style="margin-bottom: 20px">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-select v-model="evaluateModelType" placeholder="选择模型" style="width: 100%">
            <el-option value="ttranse" label="TTransE" />
            <el-option value="gru" label="GRU" />
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-button type="primary" @click="runEvaluation" :loading="evaluating">运行评估</el-button>
        </el-col>
        <el-col :span="4">
          <el-button @click="loadReports">刷新报告</el-button>
        </el-col>
      </el-row>
    </el-card>

    <el-row :gutter="20">
      <el-col :span="24" v-for="report in reports" :key="report.jobId" style="margin-bottom: 20px">
        <el-card>
          <template #header>
            {{ report.modelType }} 模型评估
            <el-tag style="margin-left: 8px">{{ report.params ? JSON.stringify(report.params).substring(0, 60) + '...' : '' }}</el-tag>
          </template>
          <el-row :gutter="20">
            <el-col :span="6">
              <div style="text-align: center; padding: 20px">
                <div style="font-size: 36px; font-weight: bold; color: #409eff">
                  {{ (report.mrr * 100).toFixed(2) }}%
                </div>
                <div style="color: #909399">MRR</div>
              </div>
            </el-col>
            <el-col :span="6">
              <div style="text-align: center; padding: 20px">
                <div style="font-size: 36px; font-weight: bold; color: #67c23a">
                  {{ (report.hitsAt1 * 100).toFixed(2) }}%
                </div>
                <div style="color: #909399">Hits@1</div>
              </div>
            </el-col>
            <el-col :span="6">
              <div style="text-align: center; padding: 20px">
                <div style="font-size: 36px; font-weight: bold; color: #e6a23c">
                  {{ (report.hitsAt3 * 100).toFixed(2) }}%
                </div>
                <div style="color: #909399">Hits@3</div>
              </div>
            </el-col>
            <el-col :span="6">
              <div style="text-align: center; padding: 20px">
                <div style="font-size: 36px; font-weight: bold; color: #f56c6c">
                  {{ (report.hitsAt10 * 100).toFixed(2) }}%
                </div>
                <div style="color: #909399">Hits@10</div>
              </div>
            </el-col>
          </el-row>

          <div v-if="report.timeAwareMetrics && Object.keys(report.timeAwareMetrics).length > 0" style="margin-top: 20px">
            <h4>按预测时间距离分组的评估</h4>
            <el-table :data="formatTimeAwareMetrics(report.timeAwareMetrics)" stripe size="small">
              <el-table-column prop="timeBucket" label="时间距离" />
              <el-table-column prop="mrr" label="MRR" />
              <el-table-column prop="hitsAt10" label="Hits@10" />
            </el-table>
          </div>

          <div v-if="report.patternMetrics && Object.keys(report.patternMetrics).length > 0" style="margin-top: 20px">
            <h4>模式挖掘评估</h4>
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item v-for="(val, key) in report.patternMetrics" :key="key" :label="key">
                {{ typeof val === 'number' ? val.toFixed(4) : val }}
              </el-descriptions-item>
            </el-descriptions>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <div v-if="reports.length === 0" style="text-align: center; color: #999; padding: 40px">
      暂无评估报告, 请先训练模型然后运行评估
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const evaluating = ref(false)
const evaluateModelType = ref('ttranse')
const reports = ref([])

async function runEvaluation() {
  evaluating.value = true
  try {
    const { data } = await api.ml.evaluate(evaluateModelType.value, {})
    ElMessage.success(`评估任务已提交, ID: ${data.id}`)
    setTimeout(loadReports, 5000)
  } catch (e) { ElMessage.error('评估失败: ' + e.message) }
  finally { evaluating.value = false }
}

async function loadReports() {
  try {
    const { data } = await api.evaluation.reports()
    reports.value = data || []
  } catch (e) { console.error(e) }
}

function formatTimeAwareMetrics(metrics) {
  return Object.entries(metrics).map(([bucket, values]) => ({
    timeBucket: bucket,
    mrr: values.mrr !== undefined ? (values.mrr * 100).toFixed(2) + '%' : '-',
    hitsAt10: values['hits@10'] !== undefined ? (values['hits@10'] * 100).toFixed(2) + '%' : '-'
  }))
}

onMounted(loadReports)
</script>
