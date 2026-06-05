<template>
  <div>
    <h2 style="margin-top: 0">图谱可视化</h2>

    <el-card style="margin-bottom: 16px">
      <el-row :gutter="16" align="middle">
        <el-col :span="6">
          <el-input v-model="searchEntity" placeholder="搜索实体名称" clearable />
        </el-col>
        <el-col :span="4">
          <el-button type="primary" @click="loadSubGraph">加载邻域</el-button>
        </el-col>
        <el-col :span="4">
          <el-select v-model="hops" style="width: 100%">
            <el-option :value="1" label="1跳" />
            <el-option :value="2" label="2跳" />
            <el-option :value="3" label="3跳" />
          </el-select>
        </el-col>
        <el-col :span="6">
          <el-button @click="loadFullGraph">加载全图(限500)</el-button>
        </el-col>
        <el-col :span="4">
          <el-button type="success" @click="playAnimation" :disabled="playing">
            {{ playing ? '播放中...' : '播放时间演化' }}
          </el-button>
        </el-col>
      </el-row>
    </el-card>

    <el-card v-if="showTimeControls" style="margin-bottom: 16px">
      <div style="display: flex; align-items: center; gap: 16px">
        <span style="white-space: nowrap; min-width: 140px">时间点: {{ currentTimeLabel }}</span>
        <el-slider
          v-model="timeSliderValue"
          :min="0"
          :max="timeSliderMax"
          @input="onSliderInput"
          style="flex: 1"
        />
        <el-button v-if="playing" size="small" @click="stopAnimation">停止</el-button>
        <el-button v-else size="small" type="primary" @click="playAnimation">播放</el-button>
      </div>
    </el-card>

    <el-card>
      <div ref="graphContainer" style="width: 100%; height: 600px; border: 1px solid #eee; position: relative">
        <div v-if="!graphData" style="display: flex; align-items: center; justify-content: center; height: 100%; color: #999">
          搜索实体或加载全图以查看图谱
        </div>
      </div>
    </el-card>

    <el-card v-if="selectedNode" style="margin-top: 16px">
      <template #header>
        实体详情: {{ selectedNode.name }}
        <el-tag style="margin-left: 8px">{{ selectedNode.type }}</el-tag>
      </template>
      <el-button type="primary" size="small" @click="loadTimeline(selectedNode.name)">查看时间线</el-button>
    </el-card>

    <el-dialog v-model="timelineVisible" :title="`事件时间线 - ${timelineEntity}`" width="80%">
      <div ref="timelineContainer" style="height: 400px"></div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import * as d3 from 'd3'
import api from '../api'

const searchEntity = ref('')
const hops = ref(2)
const graphContainer = ref(null)
const graphData = ref(null)
const selectedNode = ref(null)
const playing = ref(false)
const showTimeControls = ref(false)
const timeSliderValue = ref(0)
const timeSliderMax = ref(100)
const currentTimeLabel = ref('')
const timelineVisible = ref(false)
const timelineEntity = ref('')
const timelineContainer = ref(null)

let simulation = null
let linkElements = null
let nodeElements = null
let linkLabelElements = null
let animationTimeRange = null
let currentAnimationStep = 0

async function loadSubGraph() {
  if (!searchEntity.value) { ElMessage.warning('请输入实体名称'); return }
  try {
    const { data } = await api.graph.subgraph({ entity: searchEntity.value, hops: hops.value })
    graphData.value = data
    showTimeControls.value = false
    animationTimeRange = null
    await nextTick()
    renderGraph(data)
  } catch (e) { ElMessage.error('加载失败: ' + e.message) }
}

async function loadFullGraph() {
  try {
    const { data } = await api.graph.full({ limit: 500 })
    graphData.value = data
    showTimeControls.value = false
    animationTimeRange = null
    await nextTick()
    renderGraph(data)
  } catch (e) { ElMessage.error('加载失败: ' + e.message) }
}

async function loadTimeline(entityName) {
  timelineEntity.value = entityName
  timelineVisible.value = true
  try {
    const { data } = await api.graph.timeline({ entity: entityName })
    await nextTick()
    renderTimeline(data)
  } catch (e) { ElMessage.error('加载时间线失败') }
}

function computeTimeRange(edges) {
  let min = Infinity
  let max = -Infinity
  for (const e of edges) {
    const times = [e.timePoint, e.timeStart, e.timeEnd]
      .filter(Boolean)
      .map(t => new Date(t).getTime())
      .filter(t => !isNaN(t))
    if (times.length > 0) {
      min = Math.min(min, ...times)
      max = Math.max(max, ...times)
    }
  }
  if (min === Infinity) return null
  const pad = (max - min) * 0.05 || 86400000
  return { min: min - pad, max: max + pad }
}

function isEdgeActiveAt(edge, timestampMs) {
  if (edge.timePoint) {
    const tp = new Date(edge.timePoint).getTime()
    if (!isNaN(tp)) return tp <= timestampMs
  }
  if (edge.timeStart && edge.timeEnd) {
    const s = new Date(edge.timeStart).getTime()
    const e = new Date(edge.timeEnd).getTime()
    if (!isNaN(s) && !isNaN(e)) return timestampMs >= s && timestampMs <= e
  }
  if (edge.timeStart) {
    const s = new Date(edge.timeStart).getTime()
    if (!isNaN(s)) return s <= timestampMs
  }
  return true
}

function updateTimeHighlight(timestampMs) {
  if (!linkElements || !nodeElements) return

  const activeEntityIds = new Set()

  linkElements
    .transition()
    .duration(150)
    .attr('stroke-opacity', d => isEdgeActiveAt(d, timestampMs) ? 0.9 : 0.06)
    .attr('stroke', d => isEdgeActiveAt(d, timestampMs) ? '#409eff' : '#ddd')
    .attr('stroke-width', d => {
      const base = d.strokeWidth || 2
      return isEdgeActiveAt(d, timestampMs) ? base * 1.8 : base * 0.4
    })

  if (linkLabelElements) {
    linkLabelElements
      .transition()
      .duration(150)
      .attr('fill-opacity', d => isEdgeActiveAt(d, timestampMs) ? 1 : 0.1)
  }

  const edgeData = linkElements.data()
  if (edgeData) {
    edgeData.forEach(d => {
      if (isEdgeActiveAt(d, timestampMs)) {
        const srcId = typeof d.source === 'object' ? d.source.id : d.source
        const tgtId = typeof d.target === 'object' ? d.target.id : d.target
        if (srcId != null) activeEntityIds.add(srcId)
        if (tgtId != null) activeEntityIds.add(tgtId)
      }
    })
  }

  nodeElements
    .transition()
    .duration(150)
    .attr('opacity', d => activeEntityIds.has(d.id) ? 1 : 0.12)
}

function resetHighlight() {
  if (linkElements) {
    linkElements
      .transition()
      .duration(300)
      .attr('stroke-opacity', 0.6)
      .attr('stroke', '#999')
      .attr('stroke-width', d => d.strokeWidth || 2)
  }
  if (linkLabelElements) {
    linkLabelElements
      .transition()
      .duration(300)
      .attr('fill-opacity', 1)
  }
  if (nodeElements) {
    nodeElements
      .transition()
      .duration(300)
      .attr('opacity', 1)
  }
}

function renderGraph(data) {
  const container = graphContainer.value
  if (!container) return
  d3.select(container).selectAll('*').remove()

  linkElements = null
  nodeElements = null
  linkLabelElements = null

  const width = container.clientWidth
  const height = container.clientHeight

  const svg = d3.select(container).append('svg').attr('width', width).attr('height', height)

  const maxDegree = Math.max(...(data.nodes || []).map(n => n.degree || 1), 1)
  const nodeScale = d3.scaleSqrt().domain([1, maxDegree]).range([6, 30])

  const maxWeight = Math.max(...(data.edges || []).map(e => e.weight || 1), 1)
  const edgeScale = d3.scaleLinear().domain([1, maxWeight]).range([1, 5])

  const typeColor = d3.scaleOrdinal(d3.schemeCategory10)

  const nodes = (data.nodes || []).map(n => ({ ...n, radius: nodeScale(n.degree || 1) }))
  const edges = (data.edges || []).map(e => ({ ...e, strokeWidth: edgeScale(e.weight || 1) }))

  const nodeMap = new Map(nodes.map(n => [n.id, n]))

  simulation = d3.forceSimulation(nodes)
    .force('link', d3.forceLink(edges).id(d => d.id).distance(80))
    .force('charge', d3.forceManyBody().strength(-200))
    .force('center', d3.forceCenter(width / 2, height / 2))
    .force('collision', d3.forceCollide().radius(d => d.radius + 5))

  const linkGroup = svg.append('g')
  linkElements = linkGroup.selectAll('line')
    .data(edges)
    .join('line')
    .attr('stroke', '#999')
    .attr('stroke-width', d => d.strokeWidth)
    .attr('stroke-opacity', 0.6)

  linkLabelElements = svg.append('g').selectAll('text')
    .data(edges)
    .join('text')
    .text(d => d.relation || '')
    .attr('font-size', 10)
    .attr('fill', '#666')
    .attr('text-anchor', 'middle')

  const nodeGroup = svg.append('g')
  nodeElements = nodeGroup.selectAll('g')
    .data(nodes)
    .join('g')
    .call(d3.drag()
      .on('start', (event, d) => { if (!event.active) simulation.alphaTarget(0.3).restart(); d.fx = d.x; d.fy = d.y })
      .on('drag', (event, d) => { d.fx = event.x; d.fy = event.y })
      .on('end', (event, d) => { if (!event.active) simulation.alphaTarget(0); d.fx = null; d.fy = null })
    )

  nodeElements.append('circle')
    .attr('r', d => d.radius)
    .attr('fill', d => typeColor(d.type || 'UNKNOWN'))
    .attr('stroke', '#fff')
    .attr('stroke-width', 2)
    .on('click', (event, d) => { selectedNode.value = d })
    .on('dblclick', async (event, d) => {
      searchEntity.value = d.name
      await loadSubGraph()
    })

  nodeElements.append('text')
    .text(d => d.name.length > 8 ? d.name.substring(0, 8) + '...' : d.name)
    .attr('dy', d => d.radius + 14)
    .attr('text-anchor', 'middle')
    .attr('font-size', 11)

  simulation.on('tick', () => {
    linkElements.attr('x1', d => d.source.x).attr('y1', d => d.source.y)
        .attr('x2', d => d.target.x).attr('y2', d => d.target.y)
    linkLabelElements.attr('x', d => (d.source.x + d.target.x) / 2)
             .attr('y', d => (d.source.y + d.target.y) / 2 - 5)
    nodeElements.attr('transform', d => `translate(${d.x},${d.y})`)
  })
}

function renderTimeline(timelineData) {
    const container = timelineContainer.value
    if (!container) return
    d3.select(container).selectAll('*').remove()

    const width = container.clientWidth
    const height = 400
    const margin = { top: 30, right: 30, bottom: 60, left: 200 }

    const svg = d3.select(container).append('svg').attr('width', width).attr('height', height)

    if (!timelineData || timelineData.length === 0) return

    const events = timelineData.map((e, i) => ({
      ...e,
      time: new Date(e.timePoint || e.timeStart || Date.now()),
      index: i
    })).sort((a, b) => a.time - b.time)

    const yScale = d3.scaleBand()
      .domain(events.map((_, i) => i))
      .range([margin.top, height - margin.bottom])
      .padding(0.3)

    const xScale = d3.scaleTime()
      .domain(d3.extent(events, d => d.time))
      .range([margin.left, width - margin.right])

    svg.append('g').attr('transform', `translate(0, ${height - margin.bottom})`).call(d3.axisBottom(xScale).ticks(5))

    svg.selectAll('.event-dot')
      .data(events)
      .join('circle')
      .attr('cx', d => xScale(d.time))
      .attr('cy', d => yScale(d.index) + yScale.bandwidth() / 2)
      .attr('r', 6)
      .attr('fill', '#409eff')

    svg.selectAll('.event-label')
      .data(events)
      .join('text')
      .attr('x', margin.left - 10)
      .attr('y', d => yScale(d.index) + yScale.bandwidth() / 2 + 4)
      .attr('text-anchor', 'end')
      .attr('font-size', 11)
      .text(d => `${d.subject} - ${d.relation} - ${d.object}`)
}

async function playAnimation() {
  if (!graphData.value || !graphData.value.edges?.length) {
    await loadFullGraph()
  }
  if (!graphData.value?.edges?.length) {
    ElMessage.warning('没有图谱数据，请先加载图谱')
    return
  }

  animationTimeRange = computeTimeRange(graphData.value.edges)
  if (!animationTimeRange) {
    ElMessage.warning('图谱数据中没有时间信息，无法播放时间演化')
    return
  }

  playing.value = true
  showTimeControls.value = true
  const totalSteps = 100
  timeSliderMax.value = totalSteps

  if (currentAnimationStep > totalSteps) {
    currentAnimationStep = 0
  }

  for (let i = currentAnimationStep; i <= totalSteps && playing.value; i++) {
    currentAnimationStep = i
    timeSliderValue.value = i
    const timeMs = animationTimeRange.min + (animationTimeRange.max - animationTimeRange.min) * (i / totalSteps)
    currentTimeLabel.value = new Date(timeMs).toISOString().split('T')[0]
    updateTimeHighlight(timeMs)

    await new Promise(r => setTimeout(r, 200))
  }

  if (currentAnimationStep >= totalSteps) {
    currentAnimationStep = 0
  }
  playing.value = false
}

function onSliderInput(val) {
  if (!animationTimeRange) return
  const timeMs = animationTimeRange.min + (animationTimeRange.max - animationTimeRange.min) * (val / timeSliderMax.value)
  currentTimeLabel.value = new Date(timeMs).toISOString().split('T')[0]
  currentAnimationStep = val
  updateTimeHighlight(timeMs)
}

function stopAnimation() {
  playing.value = false
  currentAnimationStep = 0
  resetHighlight()
}
</script>
