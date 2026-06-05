<template>
  <div>
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px">
      <h2 style="margin: 0">图谱对比与演化分析</h2>
      <el-button type="success" :icon="Download" @click="exportReport" :disabled="!result">导出报告</el-button>
    </div>

    <el-card style="margin-bottom: 16px">
      <div style="padding: 8px 12px 16px">
        <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px">
          <div style="font-size: 13px; color: #666">时间范围对比: T1 到 T2</div>
          <div style="display: flex; gap: 24px">
            <div>
              <span style="font-size: 13px; color: #666; margin-right: 6px">T1:</span>
              <span style="font-weight: bold; color: #409eff">{{ formatDateLabel(sliderValues[0]) }}</span>
            </div>
            <div>
              <span style="font-size: 13px; color: #666; margin-right: 6px">T2:</span>
              <span style="font-weight: bold; color: #e6a23c">{{ formatDateLabel(sliderValues[1]) }}</span>
            </div>
          </div>
        </div>
        
        <el-slider
          v-model="sliderValues"
          :min="sliderMin"
          :max="sliderMax"
          range
          :step="86400000"
          :show-tooltip="false"
          @change="onSliderChange"
          style="margin: 8px 0 16px"
        />

        <div ref="timelineContainer" style="width: 100%; height: 40px; position: relative; margin-top: 8px">
          <div style="display: flex; align-items: center; height: 100%">
            <div style="flex: 1; height: 24px; position: relative">
              <svg ref="timelineSvg" :width="timelineWidth" height="24" style="display: block">
                <g v-if="timeRangeData">
                  <rect
                    v-for="(bar, idx) in timelineBars"
                    :key="idx"
                    :x="bar.x"
                    :y="24 - bar.height"
                    :width="bar.width"
                    :height="bar.height"
                    fill="#c0c4cc"
                    opacity="0.6"
                  />
                  <line
                    v-for="(bar, idx) in timelineBars"
                    :key="'line-' + idx"
                    :x1="bar.x + bar.width / 2"
                    :y1="0"
                    :x2="bar.x + bar.width / 2"
                    :y2="24"
                    stroke="#909399"
                    stroke-width="1"
                    opacity="0.3"
                  />
                </g>
              </svg>
              <div
                v-if="timeRangeData && timeRangeData.monthlyDistribution"
                style="display: flex; justify-content: space-between; position: absolute; left: 0; right: 0; bottom: -18px; font-size: 10px; color: #999"
              >
                <span>{{ formatMonthLabel(timeRangeData.monthlyDistribution[0]?.month) }}</span>
                <span>{{ formatMonthLabel(timeRangeData.monthlyDistribution[Math.floor(timeRangeData.monthlyDistribution.length / 2)]?.month) }}</span>
                <span>{{ formatMonthLabel(timeRangeData.monthlyDistribution[timeRangeData.monthlyDistribution.length - 1]?.month) }}</span>
              </div>
            </div>
          </div>
        </div>

        <el-row :gutter="16" align="middle" style="margin-top: 28px">
          <el-col :span="7">
            <el-input v-model="filterEntity" placeholder="过滤实体名称" clearable size="default" />
          </el-col>
          <el-col :span="7">
            <el-input v-model="filterRelation" placeholder="过滤关系类型" clearable size="default" />
          </el-col>
          <el-col :span="4">
            <el-button type="primary" @click="executeCompare" :loading="loading" style="width: 100%">执行对比</el-button>
          </el-col>
        </el-row>
      </div>
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

          <el-divider content-position="left" style="margin: 16px 0 12px">活跃度排行 Top 10</el-divider>
          <div style="max-height: 320px; overflow-y: auto">
            <div
              v-for="(ea, idx) in result.entityActivities.slice(0, 10)"
              :key="ea.entityId"
              @click="toggleEntityHighlight(ea.entityName)"
              style="display: flex; align-items: center; padding: 6px 8px; margin: 2px 0; border-radius: 4px; cursor: pointer; transition: background-color 0.2s"
              :class="{ 'highlighted-bar': highlightedEntity === ea.entityName }"
            >
              <span style="width: 24px; color: #999; font-size: 12px">{{ idx + 1 }}</span>
              <span style="flex: 1; font-weight: 500; margin-right: 8px; font-size: 13px">{{ ea.entityName }}</span>
            </div>
            <div ref="activityChartContainer" style="position: relative; margin-top: -248px; pointer-events: none">
              <svg ref="activityChartSvg" :width="308" height="248" style="display: block">
                <g v-if="result.entityActivities">
                  <g v-for="(ea, idx) in result.entityActivities.slice(0, 10)" :key="'bar-' + ea.entityId" :transform="`translate(32, ${idx * 24.8})`">
                    <rect
                      :x="0"
                      :y="4"
                      :width="getActivityBarWidth(ea.activityScore)"
                      height="18"
                      :fill="activityBarColor(ea.activityScore)"
                      rx="3"
                      :opacity="highlightedEntity && highlightedEntity !== ea.entityName ? 0.3 : 0.9"
                    />
                    <text
                      :x="getActivityBarWidth(ea.activityScore) + 6"
                      :y="17"
                      font-size="11"
                      :fill="highlightedEntity && highlightedEntity !== ea.entityName ? '#999' : '#666'"
                    >
                      {{ (ea.activityScore * 100).toFixed(1) }}%
                    </text>
                  </g>
                </g>
              </svg>
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
import { ref, onMounted, nextTick, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import * as d3 from 'd3'
import api from '../api'

const filterEntity = ref('')
const filterRelation = ref('')
const loading = ref(false)
const result = ref(null)
const graphContainer = ref(null)
const heatmapContainer = ref(null)
const graphRendered = ref(false)

const timeRangeData = ref(null)
const sliderMin = ref(0)
const sliderMax = ref(100)
const sliderValues = ref([0, 100])
const timelineContainer = ref(null)
const timelineSvg = ref(null)
const timelineWidth = ref(0)
const timelineBars = ref([])

const highlightedEntity = ref(null)
const activityChartContainer = ref(null)
const activityChartSvg = ref(null)

let linkElements = null
let linkLabels = null
let nodeElements = null
let diffEdgesGlobal = null
let heatmapCells = null
let heatmapRowLabels = null
let heatmapColLabels = null
let entityRelationTypes = new Map()

function formatTime(date) {
  if (!date) return ''
  const d = new Date(date)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function formatDateLabel(timestamp) {
  if (!timestamp) return ''
  const d = new Date(timestamp)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function formatMonthLabel(monthStr) {
  if (!monthStr) return ''
  return monthStr
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

function activityBarColor(score) {
  const r = Math.round(100 + score * 155)
  const g = Math.round(150 - score * 100)
  const b = Math.round(200 - score * 100)
  return `rgb(${r}, ${g}, ${b})`
}

function getActivityBarWidth(score) {
  return Math.max(20, score * 180)
}

function nodeColorByActivity(activity) {
  const r = Math.round(activity * 240)
  const b = Math.round((1 - activity) * 240)
  return `rgb(${r}, 60, ${b})`
}

async function loadTimeRange() {
  try {
    const { data } = await api.stats.timeRange()
    timeRangeData.value = data
    
    if (data.earliest && data.latest) {
      const earliest = new Date(data.earliest).getTime()
      const latest = new Date(data.latest).getTime()
      sliderMin.value = earliest
      sliderMax.value = latest
      sliderValues.value = [earliest, latest]
      
      await nextTick()
      renderTimeline()
    }
  } catch (e) {
    console.error('Failed to load time range:', e)
  }
}

function renderTimeline() {
  if (!timelineContainer.value || !timeRangeData.value?.monthlyDistribution) return
  
  const containerWidth = timelineContainer.value.clientWidth - 30
  timelineWidth.value = containerWidth
  
  const distribution = timeRangeData.value.monthlyDistribution
  if (distribution.length === 0) return
  
  const maxCount = Math.max(...distribution.map(d => d.count))
  const barWidth = Math.max(2, Math.min(8, containerWidth / distribution.length - 2))
  
  timelineBars.value = distribution.map((d, idx) => ({
    x: idx * (containerWidth / distribution.length) + (containerWidth / distribution.length - barWidth) / 2,
    width: barWidth,
    height: Math.max(2, (d.count / maxCount) * 20),
    count: d.count,
    month: d.month
  }))
}

function onSliderChange() {
  executeCompare()
}

async function executeCompare() {
  const t1 = sliderValues.value[0]
  const t2 = sliderValues.value[1]
  
  if (!t1 || !t2) {
    ElMessage.warning('请选择有效的时间范围')
    return
  }
  if (t1 >= t2) {
    ElMessage.warning('T1必须早于T2')
    return
  }

  loading.value = true
  highlightedEntity.value = null
  try {
    const payload = {
      t1: formatTime(t1),
      t2: formatTime(t2),
      filterEntity: filterEntity.value || null,
      filterRelation: filterRelation.value || null
    }
    const { data } = await api.comparison.compare(payload)
    result.value = data
    graphRendered.value = false
    entityRelationTypes.clear()
    
    data.addedTriples?.forEach(t => collectEntityRelations(t))
    data.deletedTriples?.forEach(t => collectEntityRelations(t))
    data.changedTriples?.forEach(t => collectEntityRelations(t))
    data.persistedTriples?.forEach(t => collectEntityRelations(t))
    
    await nextTick()
    renderDiffGraph(data)
    renderHeatmap(data)
  } catch (e) {
    ElMessage.error('对比失败: ' + (e.response?.data?.message || e.message))
  } finally {
    loading.value = false
  }
}

function collectEntityRelations(t) {
  if (!t) return
  const rel = t.relation || t.oldRelation
  if (!rel) return
  
  if (!entityRelationTypes.has(t.subject)) {
    entityRelationTypes.set(t.subject, new Set())
  }
  entityRelationTypes.get(t.subject).add(rel)
  
  if (!entityRelationTypes.has(t.object)) {
    entityRelationTypes.set(t.object, new Set())
  }
  entityRelationTypes.get(t.object).add(rel)
}

function toggleEntityHighlight(entityName) {
  if (highlightedEntity.value === entityName) {
    highlightedEntity.value = null
  } else {
    highlightedEntity.value = entityName
  }
  applyHighlight()
}

function applyHighlight() {
  const entityName = highlightedEntity.value
  
  if (linkElements) {
    if (!entityName) {
      linkElements.attr('stroke-opacity', d => d.diffType === 'PERSISTED' ? 0.4 : 0.8)
      linkLabels.attr('fill-opacity', 0.8)
      nodeElements.style('opacity', 1)
    } else {
      linkElements.attr('stroke-opacity', d => {
        const isRelated = d.source === entityName || d.target === entityName || 
                          (d.source.name && (d.source.name === entityName || d.target.name === entityName)) ||
                          (typeof d.source === 'object' && (d.source.name === entityName || d.target.name === entityName))
        return isRelated ? 1.0 : 0.1
      })
      linkLabels.attr('fill-opacity', d => {
        const isRelated = d.source === entityName || d.target === entityName ||
                          (d.source.name && (d.source.name === entityName || d.target.name === entityName)) ||
                          (typeof d.source === 'object' && (d.source.name === entityName || d.target.name === entityName))
        return isRelated ? 1.0 : 0.1
      })
      nodeElements.style('opacity', d => {
        return d.name === entityName ? 1.0 : 0.5
      })
    }
  }
  
  if (heatmapCells && result.value?.transferMatrix) {
    const relationTypes = result.value.transferMatrix.relationTypes
    const relatedRelations = entityName ? entityRelationTypes.get(entityName) || new Set() : null
    
    if (!relatedRelations) {
      heatmapCells.attr('opacity', 1)
      heatmapRowLabels.attr('opacity', 1)
      heatmapColLabels.attr('opacity', 1)
    } else {
      heatmapCells.attr('opacity', (d, i) => {
        const rowIdx = Math.floor(i / relationTypes.length)
        const colIdx = i % relationTypes.length
        const rowRel = relationTypes[rowIdx]
        const colRel = relationTypes[colIdx]
        return relatedRelations.has(rowRel) || relatedRelations.has(colRel) ? 1.0 : 0.1
      })
      heatmapRowLabels.attr('opacity', (d, i) => {
        return relatedRelations.has(relationTypes[i]) ? 1.0 : 0.3
      })
      heatmapColLabels.attr('opacity', (d, i) => {
        return relatedRelations.has(relationTypes[i]) ? 1.0 : 0.3
      })
    }
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

  diffEdgesGlobal = edges
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
  linkElements = linkGroup.selectAll('line')
    .data(diffEdges)
    .join('line')
    .attr('stroke', d => diffColorMap[d.diffType] || '#999')
    .attr('stroke-width', d => d.diffType === 'PERSISTED' ? 1.5 : 2.5)
    .attr('stroke-opacity', d => d.diffType === 'PERSISTED' ? 0.4 : 0.8)
    .attr('stroke-dasharray', d => d.diffType === 'DELETED' ? '6,3' : (d.diffType === 'PERSISTED' ? '2,2' : null))

  linkLabels = svg.append('g').selectAll('text')
    .data(diffEdges)
    .join('text')
    .text(d => d.diffType === 'CHANGED' && d.oldRelation ? `${d.oldRelation}→${d.relation}` : (d.relation || ''))
    .attr('font-size', 9)
    .attr('fill', '#666')
    .attr('text-anchor', 'middle')
    .attr('fill-opacity', 0.8)

  const nodeGroup = svg.append('g')
  nodeElements = nodeGroup.selectAll('g')
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

  heatmapCells = g.selectAll('.heatmap-cell')
    .data(types.flatMap((rowType, i) => 
      types.map((colType, j) => ({ row: i, col: j, value: matrix[i][j], rowType, colType }))
    ))
    .join('rect')
    .attr('class', 'heatmap-cell')
    .attr('x', d => d.col * cellSize)
    .attr('y', d => d.row * cellSize)
    .attr('width', cellSize - 1)
    .attr('height', cellSize - 1)
    .attr('fill', d => colorScale(d.value))
    .attr('stroke', '#fff')
    .attr('stroke-width', 0.5)
    .append('title')
    .text(d => `${types[d.row]} → ${types[d.col]}: ${d.value}`)

  g.selectAll('.heatmap-cell-text')
    .data(types.flatMap((rowType, i) => 
      types.map((colType, j) => ({ row: i, col: j, value: matrix[i][j] }))
    ))
    .join('text')
    .attr('class', 'heatmap-cell-text')
    .attr('x', d => d.col * cellSize + cellSize / 2)
    .attr('y', d => d.row * cellSize + cellSize / 2 + 4)
    .attr('text-anchor', 'middle')
    .attr('font-size', cellSize < 35 ? 9 : 11)
    .attr('fill', d => d.value > maxVal * 0.6 ? '#fff' : '#333')
    .attr('style', 'pointer-events: none')
    .text(d => d.value > 0 ? d.value : '')

  heatmapRowLabels = g.selectAll('.row-label')
    .data(types)
    .join('text')
    .attr('class', 'row-label')
    .attr('x', -4)
    .attr('y', (d, i) => i * cellSize + cellSize / 2 + 4)
    .attr('text-anchor', 'end')
    .attr('font-size', 11)
    .text(d => d.length > 12 ? d.substring(0, 12) + '..' : d)
    .append('title')
    .text(d => d)

  heatmapColLabels = g.selectAll('.col-label')
    .data(types)
    .join('text')
    .attr('class', 'col-label')
    .attr('x', (d, i) => i * cellSize + cellSize / 2)
    .attr('y', -4)
    .attr('text-anchor', 'end')
    .attr('font-size', 11)
    .attr('transform', (d, i) => `rotate(-45, ${i * cellSize + cellSize / 2}, -4)`)
    .text(d => d.length > 12 ? d.substring(0, 12) + '..' : d)
    .append('title')
    .text(d => d)
}

function exportReport() {
  if (!result.value) {
    ElMessage.warning('请先执行对比')
    return
  }
  
  const t1Date = formatDateLabel(sliderValues.value[0])
  const t2Date = formatDateLabel(sliderValues.value[1])
  
  const reportData = {
    t1: formatTime(sliderValues.value[0]),
    t2: formatTime(sliderValues.value[1]),
    diffSummary: result.value.diffSummary,
    evolutionMetrics: result.value.evolutionMetrics,
    addedTriples: result.value.addedTriples || [],
    deletedTriples: result.value.deletedTriples || [],
    changedTriples: result.value.changedTriples || [],
    persistedTriples: result.value.persistedTriples || [],
    transferMatrix: result.value.transferMatrix,
    exportedAt: new Date().toISOString()
  }
  
  const blob = new Blob([JSON.stringify(reportData, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `comparison_${t1Date}_${t2Date}.json`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
  
  ElMessage.success('报告导出成功')
}

watch(highlightedEntity, () => {
  applyHighlight()
})

onMounted(() => {
  loadTimeRange()
  window.addEventListener('resize', renderTimeline)
})
</script>

<style scoped>
.highlighted-bar {
  background-color: #ecf5ff;
  border: 1px solid #409eff;
}
</style>
