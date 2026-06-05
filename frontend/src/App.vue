<template>
  <el-container style="height: 100vh">
    <el-aside width="220px" style="background: #1d1e2c">
      <div style="padding: 20px; text-align: center; color: #fff; font-size: 16px; font-weight: bold; border-bottom: 1px solid #333">
        时序知识图谱平台
      </div>
      <el-menu
        :default-active="activeRoute"
        background-color="#1d1e2c"
        text-color="#a0a4b8"
        active-text-color="#409eff"
        router
      >
        <el-menu-item index="/">
          <el-icon><DataAnalysis /></el-icon>
          <span>仪表盘</span>
        </el-menu-item>
        <el-menu-item index="/import">
          <el-icon><Upload /></el-icon>
          <span>数据导入</span>
        </el-menu-item>
        <el-menu-item index="/graph">
          <el-icon><Share /></el-icon>
          <span>图谱可视化</span>
        </el-menu-item>
        <el-menu-item index="/query">
          <el-icon><Search /></el-icon>
          <span>图谱查询</span>
        </el-menu-item>
        <el-menu-item index="/train">
          <el-icon><Cpu /></el-icon>
          <span>模型训练</span>
        </el-menu-item>
        <el-menu-item index="/patterns">
          <el-icon><Connection /></el-icon>
          <span>模式挖掘</span>
        </el-menu-item>
        <el-menu-item index="/evaluation">
          <el-icon><TrendCharts /></el-icon>
          <span>评估报告</span>
        </el-menu-item>
        <el-menu-item index="/alerts">
          <el-icon><Bell /></el-icon>
          <span>事件预警</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-main style="padding: 20px; background: #f0f2f5; overflow: auto">
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { DataAnalysis, Upload, Share, Search, Cpu, Connection, TrendCharts, Bell } from '@element-plus/icons-vue'

const route = useRoute()
const activeRoute = computed(() => route.path)
</script>

<style>
body { margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }
.el-aside .el-menu { border-right: none; }
</style>
