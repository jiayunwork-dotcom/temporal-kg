<template>
  <div>
    <h2 style="margin-top: 0">平台概览</h2>
    <el-row :gutter="20">
      <el-col :span="6" v-for="card in statCards" :key="card.label">
        <el-card shadow="hover">
          <div style="text-align: center">
            <div style="font-size: 28px; font-weight: bold; color: #409eff">{{ card.value }}</div>
            <div style="color: #909399; margin-top: 8px">{{ card.label }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="12">
        <el-card>
          <template #header>实体类型分布</template>
          <div ref="typeChartRef" style="height: 300px"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>快速操作</template>
          <div style="display: flex; flex-direction: column; gap: 12px">
            <el-button type="primary" @click="$router.push('/import')" size="large" style="width: 100%">
              导入事件数据
            </el-button>
            <el-button type="success" @click="$router.push('/graph')" size="large" style="width: 100%">
              查看图谱可视化
            </el-button>
            <el-button type="warning" @click="$router.push('/train')" size="large" style="width: 100%">
              训练推理模型
            </el-button>
            <el-button type="info" @click="$router.push('/patterns')" size="large" style="width: 100%">
              模式挖掘
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="24">
        <el-card>
          <template #header>活跃预警</template>
          <el-table :data="alerts" stripe>
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="predictedEvent" label="预测事件" />
            <el-table-column prop="predictedTimeWindow" label="预测时间窗口" width="200" />
            <el-table-column prop="confidence" label="置信度" width="120">
              <template #default="{ row }">
                <el-tag :type="row.confidence > 0.8 ? 'danger' : row.confidence > 0.5 ? 'warning' : 'info'">
                  {{ (row.confidence * 100).toFixed(1) }}%
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-button size="small" type="danger" @click="dismissAlert(row.id)">忽略</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import * as d3 from 'd3'
import api from '../api'

const statCards = ref([
  { label: '实体总数', value: 0 },
  { label: '关系总数', value: 0 },
  { label: '三元组总数', value: 0 },
  { label: '时序模式', value: 0 }
])
const alerts = ref([])
const typeChartRef = ref(null)

onMounted(async () => {
  try {
    const { data } = await api.stats.overview()
    statCards.value = [
      { label: '实体总数', value: data.totalEntities || 0 },
      { label: '关系总数', value: data.totalRelations || 0 },
      { label: '三元组总数', value: data.totalTriples || 0 },
      { label: '时序模式', value: data.totalPatterns || 0 }
    ]

    if (typeChartRef.value && data.entityTypeDistribution) {
      renderTypeChart(data.entityTypeDistribution)
    }
  } catch (e) {
    console.error('Failed to load stats', e)
  }

  try {
    const { data } = await api.ml.getAlerts()
    alerts.value = data || []
  } catch (e) {
    console.error('Failed to load alerts', e)
  }
})

async function dismissAlert(id) {
  await api.ml.dismissAlert(id)
  alerts.value = alerts.value.filter(a => a.id !== id)
}

function renderTypeChart(distribution) {
    const container = typeChartRef.value
    if (!container) return
    const width = container.clientWidth
    const height = 300
    const entries = Object.entries(distribution)

    const svg = d3.select(container).append('svg').attr('width', width).attr('height', height)
    const pie = d3.pie().value(d => d[1])
    const arc = d3.arc().innerRadius(50).outerRadius(100)
    const color = d3.scaleOrdinal(d3.schemeCategory10)

    const g = svg.append('g').attr('transform', `translate(${width / 3}, ${height / 2})`)

    g.selectAll('path')
      .data(pie(entries))
      .join('path')
      .attr('d', arc)
      .attr('fill', d => color(d.data[0]))
      .attr('stroke', '#fff')
      .attr('stroke-width', 2)

    const legend = svg.append('g').attr('transform', `translate(${width * 2 / 3}, 30)`)
    entries.forEach(([name, count], i) => {
      legend.append('rect').attr('x', 0).attr('y', i * 25).attr('width', 16).attr('height', 16).attr('fill', color(name))
      legend.append('text').attr('x', 24).attr('y', i * 25 + 13).text(`${name}: ${count}`).attr('font-size', 13)
    })
}
</script>
