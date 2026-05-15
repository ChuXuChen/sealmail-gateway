import request from '@/utils/request'

// 获取所有配置
export function getAllConfigs() {
  return request.get('/admin/config/list')
}

// 根据分组获取配置
export function getConfigByGroup(group) {
  return request.get(`/admin/config/group/${group}`)
}

// 更新配置
export function updateConfigs(data) {
  return request.post('/admin/config/update', data)
}

// 刷新配置缓存
export function refreshConfigCache() {
  return request.post('/admin/config/refresh')
}

// 获取AI配置
export function getAiConfig() {
  return request.get('/admin/config/ai')
}
