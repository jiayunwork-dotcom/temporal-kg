<template>
  <div>
    <h2 style="margin-top: 0">图谱查询</h2>

    <el-tabs v-model="activeQuery">
      <el-tab-pane label="基础查询" name="basic">
        <el-card>
          <el-row :gutter="16">
            <el-col :span="6">
              <el-input v-model="basicQuery.entity" placeholder="实体名称" clearable />
            </el-col>
            <el-col :span="4">
              <el-input v-model="basicQuery.relation" placeholder="关系类型" clearable />
            </el-col>
            <el-col :span="5">
              <el-input v-model="basicQuery.timeStart" placeholder="开始时间" />
            </el-col>
            <el-col :span="5">
              <el-input v-model="basicQuery.timeEnd" placeholder="结束时间" />
            </el-col>
            <el-col :span="4">
              <el-button type="primary" @click="doBasicQuery">查询</el-button>
            </el-col>
          </el-row>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="路径查询" name="path">
        <el-card>
          <el-row :gutter="16">
            <el-col :span="5">
              <el-input v-model="pathQuery.from" placeholder="起始实体" />
            </el-col>
            <el-col :span="5">
              <el-input v-model="pathQuery.to" placeholder="目标实体" />
            </el-col>
            <el-col :span="3">
              <el-input-number v-model="pathQuery.maxHops" :min="1" :max="10" />
            </el-col>
            <el-col :span="4">
              <el-switch v-model="pathQuery.shortestOnly" active-text="最短路径" inactive-text="所有路径" />
            </el-col>
            <el-col :span="3">
              <el-button type="primary" @click="doPathQuery">查询</el-button>
            </el-col>
          </el-row>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="子图提取" name="subgraph">
        <el-card>
          <el-row :gutter="16">
            <el-col :span="8">
              <el-input v-model="subgraphQuery.entity" placeholder="中心实体" />
            </el-col>
            <el-col :span="4">
              <el-input-number v-model="subgraphQuery.hops" :min="1" :max="5" />
            </el-col>
            <el-col :span="4">
              <el-button type="primary" @click="doSubgraphQuery">提取</el-button>
            </el-col>
          </el-row>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="时间切片" name="timeslice">
        <el-card>
          <el-row :gutter="16">
            <el-col :span="10">
              <el-input v-model="timeSliceQuery.timePoint" placeholder="时间点 (如: 2024-06-01T00:00:00Z)" />
            </el-col>
            <el-col :span="4">
              <el-button type="primary" @click="doTimeSliceQuery">查询快照</el-button>
            </el-col>
          </el-row>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="实体时间线" name="timeline">
        <el-card>
          <el-row :gutter="16">
            <el-col :span="10">
              <el-input v-model="timelineQuery.entity" placeholder="实体名称" />
            </el-col>
            <el-col :span="4">
              <el-button type="primary" @click="doTimelineQuery">查询时间线</el-button>
            </el-col>
          </el-row>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-card style="margin-top: 16px">
      <template #header>
        查询结果
        <span v-if="results" style="color: #909399; margin-left: 8px">共 {{ results.length }} 条</span>
      </template>

      <div v-if="pathResults.length > 0">
        <div v-for="(path, idx) in pathResults" :key="idx" style="margin-bottom: 16px">
          <h4>路径 {{ idx + 1 }} (长度: {{ path.length }})</h4>
          <el-tag v-for="(node, ni) in path.nodes" :key="ni" style="margin: 4px">
            {{ node.name }}
            <span v-if="ni < path.nodes.length - 1" style="margin-left: 4px">
              → {{ path.edges[ni]?.relation || '' }} →
            </span>
          </el-tag>
        </div>
      </div>

      <el-table v-else-if="results && results.length > 0" :data="results" stripe max-height="500">
        <el-table-column prop="subject" label="主体" width="150" />
        <el-table-column prop="relation" label="关系" width="120" />
        <el-table-column prop="object" label="客体" width="150" />
        <el-table-column prop="timePoint" label="时间点" width="200" />
        <el-table-column prop="timeStart" label="开始时间" width="200" />
        <el-table-column prop="timeEnd" label="结束时间" width="200" />
        <el-table-column prop="confidence" label="置信度" width="100" />
        <el-table-column prop="source" label="来源" />
      </el-table>

      <div v-else style="text-align: center; color: #999; padding: 40px">
        请选择查询类型并输入参数
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const activeQuery = ref('basic')
const results = ref(null)
const pathResults = ref([])

const basicQuery = ref({ entity: '', relation: '', timeStart: '', timeEnd: '' })
const pathQuery = ref({ from: '', to: '', maxHops: 5, shortestOnly: true })
const subgraphQuery = ref({ entity: '', hops: 2 })
const timeSliceQuery = ref({ timePoint: '' })
const timelineQuery = ref({ entity: '' })

async function doBasicQuery() {
  try {
    const { data } = await api.graph.query(basicQuery.value)
    results.value = data
    pathResults.value = []
  } catch (e) { ElMessage.error('查询失败') }
}

async function doPathQuery() {
  if (!pathQuery.value.from || !pathQuery.value.to) { ElMessage.warning('请输入起始和目标实体'); return }
  try {
    const { data } = await api.graph.paths(pathQuery.value)
    pathResults.value = data
    results.value = null
  } catch (e) { ElMessage.error('查询失败') }
}

async function doSubgraphQuery() {
  if (!subgraphQuery.value.entity) { ElMessage.warning('请输入中心实体'); return }
  try {
    const { data } = await api.graph.subgraph(subgraphQuery.value)
    ElMessage.success(`子图包含 ${data.nodes?.length || 0} 个节点, ${data.edges?.length || 0} 条边`)
  } catch (e) { ElMessage.error('查询失败') }
}

async function doTimeSliceQuery() {
  if (!timeSliceQuery.value.timePoint) { ElMessage.warning('请输入时间点'); return }
  try {
    const { data } = await api.graph.timeSlice(timeSliceQuery.value)
    ElMessage.success(`快照包含 ${data.nodes?.length || 0} 个节点, ${data.edges?.length || 0} 条边`)
  } catch (e) { ElMessage.error('查询失败') }
}

async function doTimelineQuery() {
  if (!timelineQuery.value.entity) { ElMessage.warning('请输入实体名称'); return }
  try {
    const { data } = await api.graph.timeline(timelineQuery.value)
    results.value = data
    pathResults.value = []
  } catch (e) { ElMessage.error('查询失败') }
}
</script>
