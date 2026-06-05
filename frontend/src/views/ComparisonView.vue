<template>
  <div>
    <h2 style="margin-top: 0">图谱对比与演化分析</h2>

    <el-card style="margin-bottom: 16px">
      <el-row :gutter="16" align="middle">
        <el-col :span="7">
          <span style="font-size: 13px; color: #666; margin-right: 6px">T1:</span>
          <el-date-picker v-model="t1" type="datetime" placeholder="选择时间点T1" style="width: 100%" />
        </el-col>
        <el-col :span="7">
          <span style="font-size: 13px; color: #666; margin-right: 6px">T2:</span>
          <el-date-picker v-model="t2" type="datetime" placeholder="选择时间点T2" style="width: 100%" />
        </el-col>
        <el-col :span="4">
          <el-input v-model="filterEntity" placeholder="过滤实体名称" clearable size="default" />
        </el-col>
        <el-col :span="4">
          <el-input v-model="filterRelation" placeholder="过滤关系类型" clearable size="default" />
        </el-col>
        <el-col :span="2">
          <el-button type="primary" @click="executeCompare" :loading="loading" style="width: 100%">执行对比</el-button>
        </el-col>
      </el-row>
    </el-card>

    <template v-if="result">
      <div style="display: flex; gap: 16px; margin-bottom: 16px">
        <el-card style="width: 360px; min-width: 360px">
          <template #header><span style="font-weight: bold">对比统计</span></template>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="新增三元组">
              <span style="color: #67c23a; font-weight: bold">{{ result.diffSummary.addedCount }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="删除三元组">
              <span style="color: #f56c6c; font-weight: bold">{{ result.diffSummary.deletedCount }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="持续三元组">
              <span style="color: #909399; font-weight: bold">{{ result.diffSummary.persistedCount }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="变更三元组">
              <span style="color: #e6a23c; font-weight: bold">{{ result.diffSummary.changedCount }}</span>
            </el-descriptions-item>
          </el-descriptions>

          <el-divider content-position="left" style="margin: 16px 0 12px">演化指标</el-divider>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="图谱密度变化率">
              {{ formatPercent(result.evolutionMetrics.densityChangeRate) }}
              <span style="color: #999; font-size: 11px; margin-left: 4px">
                (T1: {{ formatDensity(result.evolutionMetrics.t1Density) }} → T2: {{ formatDensity(result.evolutionMetrics.t2Density) }})
              </span>
            </el-descriptions-item>
            <el-descriptions-item label="中心性漂移(Kendall τ)">
              {{ result.evolutionMetrics.centralityDrift?.toFixed(4) }}
            </el-descriptions-item>
            <el-descriptions-item label="社区稳定性(NMI)">
              {{ result.evolutionMetrics.communityStability?.toFixed(4) }}
            </el-descriptions-item>
          </el-descriptions>

          <el-divider content-position="left" style="margin: 16px 0 12px">实体活跃度 Top 10</el-divider>
          <div style="max-height: 220px; overflow-y: auto">
            <div v-for="(ea, idx) in result.entityActivities.slice(0, 10)" :key="ea.entityId"
                 style="display: flex; align-items: center; padding: 4px 0; border-bottom: 1px solid #f5f5f5; font-size: 13px">
              <span style="width: 24px; color: #999; font-size: 12px">{{ idx + 1 }}</span>
              <span style="flex: 1; font-weight: 500">{{ ea.entityName }}</span>
              <el-progress :percentage="ea.activityScore * 100" :stroke-width="8" :show-text="false"
                           style="width: 80px; margin-right: 8px" :color="activityColor(ea.activityScore)" />
              <span style="font-size: 12px; color: #999; min-width: 48px; text-align: right">
                {{ (ea.activityScore * 100).toFixed(1) }}%
              </span>
            </div>
          </div>
        </el-card>

        <el-card style="flex: 1; min-width: 0">
          <template #header>
            <div style="display: flex; justify-content: space-between; align-items: center">
              <span style="font-weight: bold">差异图谱视图</span>
              <div style="display: flex; gap: 16px; font-size: 12px; color: #666">
                <span><span style="display: inline-block; width: 12px; height: 3px; background: #67c23a; vertical-align: middle; margin-right: 4px"></span>新增</span>
                <span><span style="display: inline-block; width: 12px; height: 3px; background: #f56c6c; vertical-align: middle; margin-right: 4px"></span>删除</span>
                <span><span style="display: inline-block; width: 12px; height: 3px; background: #e6a23c; vertical-align: middle; margin-right: 4px"></span>变更</span>
                <span><span style="display: inline-block; width: 12px; height: 3px; background: #c0c4cc; vertical-align: middle; margin-right: 4px"></span>持续</span>
              </div>
            </div>
          </template>
          <div ref="graphContainer" style="width: 100%; height: 480px; border: 1px solid #eee; position: relative">
            <div v-if="!graphRendered" style="display: flex; align-items: center; justify-content: center; height: 100%; color: #999">
              点击"执行对比"查看差异图谱
            </div>
          </div>
        </el-card>
      </div>

      <el-card>
        <template #header><span style="font-weight: bold">关系类型转移矩阵</span></template>
        <div ref="heatmapContainer" style="width: 100%; overflow: auto">
          <div v-if="!result.transferMatrix || result.transferMatrix.relationTypes.length === 0"
               style="text-align: center; color: #999; padding: 40px">
            无关系类型数据
          </div>
        </div>
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import * as d3 from 'd3'
import api from '../api'

const t1 = ref(null)
const t2 = ref(null)
const filterEntity = ref('')
const filterRelation = ref('')
const loading = ref(false)
const result = ref(null)
const graphContainer = ref(null)
const heatmapContainer = ref(null)
const graphRendered = ref(false)

function formatTime(date) {
  if (!date) return ''
  const d = new Date(date)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function formatPercent(val) {
  if (val == null) return 'N/A'
  const prefix = val >= 0 ? '+' : ''
  return `${prefix}${val.toFixed(2)}%`
}

function formatDensity(val) {
  if (val == null) return 'N/A'
  return val.toFixed(6)
}

function activityColor(score) {
  if (score > 0.7) return '#f56c6c'
  if (score > 0.4) return '#e6a23c'
  return '#409eff'
}

function nodeColorByActivity(activity) {
  const r = Math.round(activity * 240)
  const b = Math.round((1 - activity) * 240)
  return `rgb(${r}, 60, ${b})`
}

async function executeCompare() {
  if (!t1.value || !t2.value) {
    ElMessage.warning('请选择T1和T2时间点')
    return
  }
  if (new Date(t1.value) >= new Date(t2.value)) {
    ElMessage.warning('T1必须早于T2')
    return
  }

  loading.value = true
  try {
    const payload = {
      t1: formatTime(t1.value),
      t2: formatTime(t2.value),
      filterEntity: filterEntity.value || null,
      filterRelation: filterRelation.value || null
    }
    const { data } = await api.comparison.compare(payload)
    result.value = data
    graphRendered.value = false
    await nextTick()
    renderDiffGraph(data)
    renderHeatmap(data)
  } catch (e) {
    ElMessage.error('对比失败: ' + (e.response?.data?.message || e.message))
  } finally {
    loading.value = false
  }
}

function buildDiffEdges(result) {
  const edges = []
  const edgeSet = new Set()

  const addEdges = (triples, diffType) => {
    for (const t of triples) {
      const key = `${t.subject}-${t.object}-${diffType}`
      if (edgeSet.has(key)) continue
      edgeSet.add(key)
      edges.push({
        source: t.subject,
        target: t.object,
        relation: t.relation,
        diffType,
        oldRelation: t.oldRelation || null
      })
    }
  }

  addEdges(result.addedTriples || [], 'ADDED')
  addEdges(result.deletedTriples || [], 'DELETED')
  addEdges(result.changedTriples || [], 'CHANGED')
  addEdges(result.persistedTriples || [], 'PERSISTED')

  return edges
}

function renderDiffGraph(data) {
  const container = graphContainer.value
  if (!container) return

  d3.select(container).selectAll('svg').remove()
  graphRendered.value = true

  const width = container.clientWidth
  const height = container.clientHeight

  const diffEdges = buildDiffEdges(data)

  if (diffEdges.length === 0 && (!data.nodes || data.nodes.length === 0)) {
    d3.select(container).append('div')
      .style('display', 'flex')
      .style('align-items', 'center')
      .style('justify-content', 'center')
      .style('height', height + 'px')
      .style('color', '#999')
      .text('无差异数据')
    return
  }

  const nodeMap = new Map()
  for (const n of data.nodes || []) {
    nodeMap.set(n.name, n)
  }

  const activeNames = new Set()
  for (const e of diffEdges) {
    activeNames.add(e.source)
    activeNames.add(e.target)
  }

  const nodes = Array.from(activeNames).map(name => {
    const n = nodeMap.get(name)
    return {
      id: name,
      name,
      type: n?.type || 'UNKNOWN',
      activity: n?.activity || 0
    }
  })

  const activityScale = d3.scaleLinear().domain([0, 1]).range([6, 24])

  const simulation = d3.forceSimulation(nodes)
    .force('link', d3.forceLink(diffEdges).id(d => d.id || d.source).distance(80))
    .force('charge', d3.forceManyBody().strength(-180))
    .force('center', d3.forceCenter(width / 2, height / 2))
    .force('collision', d3.forceCollide().radius(d => activityScale(d.activity) + 4))

  const svg = d3.select(container).append('svg').attr('width', width).attr('height', height)

  const diffColorMap = {
    ADDED: '#67c23a',
    DELETED: '#f56c6c',
    CHANGED: '#e6a23c',
    PERSISTED: '#c0c4cc'
  }

  const linkGroup = svg.append('g')
  const linkElements = linkGroup.selectAll('line')
    .data(diffEdges)
    .join('line')
    .attr('stroke', d => diffColorMap[d.diffType] || '#999')
    .attr('stroke-width', d => d.diffType === 'PERSISTED' ? 1.5 : 2.5)
    .attr('stroke-opacity', d => d.diffType === 'PERSISTED' ? 0.4 : 0.8)
    .attr('stroke-dasharray', d => d.diffType === 'DELETED' ? '6,3' : (d.diffType === 'PERSISTED' ? '2,2' : null))

  const linkLabels = svg.append('g').selectAll('text')
    .data(diffEdges)
    .join('text')
    .text(d => d.diffType === 'CHANGED' && d.oldRelation ? `${d.oldRelation}→${d.relation}` : (d.relation || ''))
    .attr('font-size', 9)
    .attr('fill', '#666')
    .attr('text-anchor', 'middle')
    .attr('fill-opacity', 0.8)

  const nodeGroup = svg.append('g')
  const nodeElements = nodeGroup.selectAll('g')
    .data(nodes, d => d.id)
    .join('g')
    .style('cursor', 'pointer')

  nodeElements.call(d3.drag()
    .on('start', (event, d) => { if (!event.active) simulation.alphaTarget(0.3).restart(); d.fx = d.x; d.fy = d.y })
    .on('drag', (event, d) => { d.fx = event.x; d.fy = event.y })
    .on('end', (event, d) => { if (!event.active) simulation.alphaTarget(0); d.fx = null; d.fy = null })
  )

  nodeElements.append('circle')
    .attr('r', d => activityScale(d.activity))
    .attr('fill', d => nodeColorByActivity(d.activity))
    .attr('stroke', '#fff')
    .attr('stroke-width', 1.5)

  nodeElements.append('text')
    .text(d => d.name.length > 10 ? d.name.substring(0, 10) + '...' : d.name)
    .attr('dy', d => activityScale(d.activity) + 14)
    .attr('text-anchor', 'middle')
    .attr('font-size', 10)
    .style('pointer-events', 'none')

  nodeElements.append('title')
    .text(d => `${d.name}\n活跃度: ${(d.activity * 100).toFixed(1)}%`)

  simulation.on('tick', () => {
    linkElements
      .attr('x1', d => d.source.x).attr('y1', d => d.source.y)
      .attr('x2', d => d.target.x).attr('y2', d => d.target.y)
    linkLabels
      .attr('x', d => (d.source.x + d.target.x) / 2)
      .attr('y', d => (d.source.y + d.target.y) / 2 - 5)
    nodeElements.attr('transform', d => `translate(${d.x},${d.y})`)
  })
}

function renderHeatmap(data) {
  const container = heatmapContainer.value
  if (!container) return

  d3.select(container).selectAll('svg').remove()

  const tm = data.transferMatrix
  if (!tm || !tm.relationTypes || tm.relationTypes.length === 0) return

  const types = tm.relationTypes
  const matrix = tm.matrix
  const n = types.length

  let maxVal = 0
  for (const row of matrix) {
    for (const v of row) {
      if (v > maxVal) maxVal = v
    }
  }
  if (maxVal === 0) maxVal = 1

  const cellSize = Math.min(60, Math.max(28, 600 / n))
  const labelWidth = 120
  const labelHeight = 100
  const svgWidth = labelWidth + n * cellSize + 40
  const svgHeight = labelHeight + n * cellSize + 20

  const svg = d3.select(container).append('svg')
    .attr('width', svgWidth)
    .attr('height', svgHeight)

  const colorScale = d3.scaleSequential(d3.interpolateYlOrRd).domain([0, maxVal])

  const g = svg.append('g').attr('transform', `translate(${labelWidth}, ${labelHeight})`)

  for (let i = 0; i < n; i++) {
    for (let j = 0; j < n; j++) {
      const val = matrix[i][j]
      g.append('rect')
        .attr('x', j * cellSize)
        .attr('y', i * cellSize)
        .attr('width', cellSize - 1)
        .attr('height', cellSize - 1)
        .attr('fill', colorScale(val))
        .attr('stroke', '#fff')
        .attr('stroke-width', 0.5)
        .append('title')
        .text(`${types[i]} → ${types[j]}: ${val}`)
    }
  }

  for (let i = 0; i < n; i++) {
    for (let j = 0; j < n; j++) {
      const val = matrix[i][j]
      if (val > 0) {
        g.append('text')
          .attr('x', j * cellSize + cellSize / 2)
          .attr('y', i * cellSize + cellSize / 2 + 4)
          .attr('text-anchor', 'middle')
          .attr('font-size', cellSize < 35 ? 9 : 11)
          .attr('fill', val > maxVal * 0.6 ? '#fff' : '#333')
          .attr('style', 'pointer-events: none')
          .text(val)
      }
    }
  }

  g.selectAll('.row-label')
    .data(types)
    .join('text')
    .attr('x', -4)
    .attr('y', (d, i) => i * cellSize + cellSize / 2 + 4)
    .attr('text-anchor', 'end')
    .attr('font-size', 11)
    .text(d => d.length > 12 ? d.substring(0, 12) + '..' : d)
    .append('title')
    .text(d => d)

  g.selectAll('.col-label')
    .data(types)
    .join('text')
    .attr('x', (d, i) => i * cellSize + cellSize / 2)
    .attr('y', -4)
    .attr('text-anchor', 'end')
    .attr('font-size', 11)
    .attr('transform', (d, i) => `rotate(-45, ${i * cellSize + cellSize / 2}, -4)`)
    .text(d => d.length > 12 ? d.substring(0, 12) + '..' : d)
    .append('title')
    .text(d => d)
}
</script>

<style scoped>
</style>
