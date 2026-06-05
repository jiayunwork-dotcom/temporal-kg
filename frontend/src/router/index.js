import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', name: 'Dashboard', component: () => import('../views/Dashboard.vue') },
  { path: '/import', name: 'Import', component: () => import('../views/ImportView.vue') },
  { path: '/graph', name: 'Graph', component: () => import('../views/GraphView.vue') },
  { path: '/query', name: 'Query', component: () => import('../views/QueryView.vue') },
  { path: '/train', name: 'Train', component: () => import('../views/TrainView.vue') },
  { path: '/patterns', name: 'Patterns', component: () => import('../views/PatternView.vue') },
  { path: '/evaluation', name: 'Evaluation', component: () => import('../views/EvaluationView.vue') },
  { path: '/alerts', name: 'Alerts', component: () => import('../views/AlertView.vue') },
  { path: '/comparison', name: 'Comparison', component: () => import('../views/ComparisonView.vue') },
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
