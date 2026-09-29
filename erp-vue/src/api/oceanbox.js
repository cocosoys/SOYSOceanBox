import request from '@/utils/request'

/**
 * SOYSOceanBox 扩展 API
 * baseURL 由 SOYS 契约注入（生产 = /api/plugins/soysoceanbox，开发 = /dev-api 代理）
 * 路径与后端 OceanBoxController 端点一一对应。
 */

/* ================= 用户侧（Hypixel 页面，本 ERP 不直接使用，预留） ================= */
export function viewPools() {
  return request({ url: '/view/pools', method: 'get' })
}
export function playerState() {
  return request({ url: '/player/state', method: 'get' })
}
export function playerDraw(pool, times) {
  return request({ url: `/player/draw/${pool}`, method: 'post', data: { times } })
}
export function playerClaim(target) {
  return request({ url: '/player/claim', method: 'post', data: { target: target || 'all' } })
}

/* ================= 管理侧：概览 / 列表 ================= */
export function adminOverview() {
  return request({ url: '/admin/overview', method: 'get' })
}
export function adminPools() {
  return request({ url: '/admin/pools', method: 'get' })
}

/* ================= 管理侧：奖池详情 / 保存 / 规则 / 启停 ================= */
export function getPool(pool) {
  return request({ url: `/admin/pools/${pool}`, method: 'get' })
}
export function savePool(pool, data) {
  return request({ url: `/admin/pools/${pool}`, method: 'put', data })
}
export function getPoolConfig(pool) {
  return request({ url: `/admin/pools/${pool}/config`, method: 'get' })
}
export function savePoolConfig(pool, data) {
  return request({ url: `/admin/pools/${pool}/config`, method: 'put', data })
}
export function togglePool(pool) {
  return request({ url: `/admin/pools/${pool}/toggle`, method: 'post' })
}

/* ================= 管理侧：代抽 / 直接发放 / 重置 / 记录 ================= */
export function drawFor(pool, player, times) {
  return request({ url: `/admin/pools/${pool}/draw`, method: 'post', data: { player, times } })
}
// 直接发放：rewardId 指定奖池既有奖项，或 reward 传完整物品信息
export function giveFor(pool, player, payload) {
  return request({ url: `/admin/pools/${pool}/give`, method: 'post', data: Object.assign({ player }, payload) })
}
export function resetFor(pool, player, scopes) {
  return request({ url: `/admin/pools/${pool}/reset`, method: 'post', data: { player, scopes } })
}
export function poolRecords(pool) {
  return request({ url: `/admin/pools/${pool}/records`, method: 'get' })
}

/* ================= 管理侧：设置 / 日志 / 背包 / 玩家 ================= */
export function getSettings() {
  return request({ url: '/admin/settings', method: 'get' })
}
export function saveSettings(data) {
  return request({ url: '/admin/settings', method: 'put', data })
}
export function getLogs() {
  return request({ url: '/admin/logs', method: 'get' })
}
export function getInventory() {
  return request({ url: '/admin/inventory', method: 'get' })
}
export function onlinePlayers() {
  return request({ url: '/admin/players', method: 'get' })
}
