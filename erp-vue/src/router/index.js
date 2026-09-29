import Vue from 'vue'
import Router from 'vue-router'

Vue.use(Router)

/* Layout：wujie 嵌入时主应用 MCERP 提供侧边栏（空 Layout）；直接访问时用完整 Layout（自带侧边栏） */
import RealLayout from '@/layout'
import EmptyLayout from '@/layout/Empty'

// wujie 子应用标记（主应用加载时注入 window.__POWERED_BY_WUJIE__）
export const isWujie = typeof window !== 'undefined' && !!window.__POWERED_BY_WUJIE__
const Layout = isWujie ? EmptyLayout : RealLayout

/**
 * 公共路由：所有模式都加载。
 * OceanBox 业务菜单（礼包列表 / 设置 / 日志）常驻 constantRoutes——
 * wujie 模式由主应用统一鉴权直接放行；独立模式登录后也无需依赖后端动态下发即可显示。
 */
export const constantRoutes = [
  {
    path: '/redirect',
    component: Layout,
    hidden: true,
    children: [
      {
        path: '/redirect/:path(.*)',
        component: () => import('@/views/redirect')
      }
    ]
  },
  {
    path: '/login',
    component: () => import('@/views/login'),
    hidden: true
  },
  {
    path: '/register',
    component: () => import('@/views/register'),
    hidden: true
  },
  {
    path: '/404',
    component: () => import('@/views/error/404'),
    hidden: true
  },
  {
    path: '/401',
    component: () => import('@/views/error/401'),
    hidden: true
  },
  {
    // 礼包（奖池）列表——ERP 首页
    path: '',
    component: Layout,
    redirect: '/pools',
    children: [
      {
        path: '/pools',
        component: () => import('@/views/soysoceanbox/pools/index'),
        name: 'Pools',
        meta: { title: '礼包列表', icon: 'shopping', affix: true }
      }
    ]
  },
  {
    path: '/settings',
    component: Layout,
    children: [
      {
        path: '',
        component: () => import('@/views/soysoceanbox/settings/index'),
        name: 'Settings',
        meta: { title: '设置', icon: 'system' }
      }
    ]
  },
  {
    path: '/logs',
    component: Layout,
    children: [
      {
        path: '',
        component: () => import('@/views/soysoceanbox/logs/index'),
        name: 'Logs',
        meta: { title: '日志', icon: 'log' }
      }
    ]
  },
  {
    path: '/lock',
    component: () => import('@/views/lock'),
    hidden: true,
    meta: { title: '锁定屏幕' }
  }
]

/**
 * wujie 业务路由兜底：OceanBox 业务已常驻 constantRoutes，无需额外下发。
 */
export const wujieRoutes = []

// 动态路由（预留）
export const dynamicRoutes = []

// 防止连续点击多次路由报错
let routerPush = Router.prototype.push
let routerReplace = Router.prototype.replace
Router.prototype.push = function push(location) {
  return routerPush.call(this, location).catch(err => err)
}
Router.prototype.replace = function replace(location) {
  return routerReplace.call(this, location).catch(err => err)
}

export default new Router({
  mode: 'hash', // hash 模式：不依赖服务端 history 回退，wujie 嵌入与独立运行、深层刷新均可用
  scrollBehavior: () => ({ y: 0 }),
  routes: constantRoutes
})
