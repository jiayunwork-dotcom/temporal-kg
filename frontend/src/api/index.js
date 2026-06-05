import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 60000
})

api.interceptors.response.use(
  response => response,
  error => {
    console.error('API Error:', error)
    return Promise.reject(error)
  }
)

export default {
  stats: {
    overview: () => api.get('/stats/overview')
  },
  import: {
    uploadCsv: (file) => {
      const formData = new FormData()
      formData.append('file', file)
      return api.post('/import/csv', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
    },
    uploadJson: (file) => {
      const formData = new FormData()
      formData.append('file', file)
      return api.post('/import/json', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
    },
    importEvents: (events) => api.post('/import/events', events),
    listJobs: () => api.get('/import/jobs'),
    getJob: (id) => api.get(`/import/jobs/${id}`)
  },
  graph: {
    query: (params) => api.get('/graph/query', { params }),
    paths: (params) => api.get('/graph/paths', { params }),
    subgraph: (params) => api.get('/graph/subgraph', { params }),
    timeSlice: (params) => api.get('/graph/time-slice', { params }),
    timeline: (params) => api.get('/graph/timeline', { params }),
    full: (params) => api.get('/graph/full', { params }),
    patternMatch: (pattern) => api.post('/graph/pattern-match', pattern)
  },
  ml: {
    train: (modelType, params) => api.post(`/ml/train?modelType=${modelType}`, params),
    predict: (data) => api.post('/ml/predict', data),
    evaluate: (modelType, params) => api.post(`/ml/evaluate?modelType=${modelType}`, params),
    minePatterns: (params) => api.post('/ml/mine-patterns', params),
    listJobs: () => api.get('/ml/jobs'),
    getJob: (id) => api.get(`/ml/jobs/${id}`),
    ingestEvent: (event) => api.post('/ml/events', event),
    ingestBatch: (events) => api.post('/ml/events/batch', events),
    getAlerts: () => api.get('/ml/alerts'),
    dismissAlert: (id) => api.delete(`/ml/alerts/${id}`)
  },
  patterns: {
    list: (params) => api.get('/patterns', { params }),
    get: (id) => api.get(`/patterns/${id}`)
  },
  evaluation: {
    report: (jobId) => api.get(`/evaluation/report/${jobId}`),
    reports: () => api.get('/evaluation/reports')
  }
}
