<template>
  <div class="app-container">
    <el-card shadow="never" v-loading="loading">
      <el-form :model="form" label-width="150px" size="small">

        <el-tabs v-model="tab">
          <!-- ===== 基础规则 ===== -->
          <el-tab-pane label="基础规则" name="base">
            <el-form-item label="抽奖冷却（秒）">
              <el-input-number v-model="form.cooldownSeconds" :min="0" :max="86400" controls-position="right"/>
              <span class="tip">两次抽奖最小间隔，0 = 无冷却</span>
            </el-form-item>
            <el-form-item label="中奖全服广播">
              <el-switch v-model="form.broadcastBigWin"/>
            </el-form-item>
            <el-form-item label="广播权重阈值">
              <el-input-number v-model="form.broadcastBelowWeight" :min="0" :max="100000" controls-position="right"/>
              <span class="tip">仅权重 ≤ 该值的稀有奖项广播，0 = 全部广播</span>
            </el-form-item>
          </el-tab-pane>

          <!-- ===== 抽奖消耗 ===== -->
          <el-tab-pane label="抽奖消耗" name="cost">
            <el-form-item label="启用消耗">
              <el-switch v-model="form.cost.enabled"/>
            </el-form-item>
            <el-form-item label="消耗类型">
              <el-radio-group v-model="form.cost.type">
                <el-radio label="money">金币（Vault）</el-radio>
                <el-radio label="points">点券（PlayerPoints）</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="每次消耗量">
              <el-input-number v-model="form.cost.amount" :min="0" :step="100" controls-position="right"/>
            </el-form-item>
          </el-tab-pane>

          <!-- ===== 次数与保底 ===== -->
          <el-tab-pane label="次数与保底" name="limit">
            <el-divider content-position="left">每日上限</el-divider>
            <el-form-item label="启用每日上限">
              <el-switch v-model="form.limits.daily.enabled"/>
            </el-form-item>
            <el-form-item label="每日次数">
              <el-input-number v-model="form.limits.daily.amount" :min="0" controls-position="right"/>
            </el-form-item>

            <el-divider content-position="left">每周上限</el-divider>
            <el-form-item label="启用每周上限">
              <el-switch v-model="form.limits.weekly.enabled"/>
            </el-form-item>
            <el-form-item label="每周次数">
              <el-input-number v-model="form.limits.weekly.amount" :min="0" controls-position="right"/>
            </el-form-item>

            <el-divider content-position="left">概率保底</el-divider>
            <el-form-item label="启用保底">
              <el-switch v-model="form.pityEnabled"/>
            </el-form-item>
            <el-form-item label="保底触发次数">
              <el-input-number v-model="form.pityAfter" :min="1" controls-position="right"/>
              <span class="tip">连续未中保底池奖项达到该次数后，下次强制中保底奖项</span>
            </el-form-item>
          </el-tab-pane>

          <!-- ===== 待领取与记录 ===== -->
          <el-tab-pane label="待领取与记录" name="store">
            <el-form-item label="待领取过期（秒）">
              <el-input-number v-model="form.pending['expire-after']" :min="0" :step="3600" controls-position="right"/>
              <span class="tip">0 = 永不过期；如 86400 = 保留 1 天</span>
            </el-form-item>
            <el-form-item label="中奖记录保留条数">
              <el-input-number v-model="form.history.keep" :min="0" controls-position="right"/>
              <span class="tip">0 = 不记录历史</span>
            </el-form-item>
          </el-tab-pane>

          <!-- ===== 前置条件 ===== -->
          <el-tab-pane label="前置条件" name="require">
            <el-form-item label="所需权限节点">
              <el-input v-model="form.require.permission" placeholder="如 soysoceanbox.vip，留空不限制" style="max-width:360px"/>
            </el-form-item>
            <el-form-item label="所需经验等级">
              <el-input-number v-model="form.require.level" :min="0" controls-position="right"/>
            </el-form-item>
            <el-form-item label="所需物品材质">
              <el-input v-model="form.require.item.material" placeholder="如 DIAMOND，留空不限制" style="max-width:360px"/>
            </el-form-item>
            <el-form-item label="所需物品数量">
              <el-input-number v-model="form.require.item.amount" :min="1" controls-position="right"/>
            </el-form-item>
          </el-tab-pane>
        </el-tabs>
      </el-form>

      <div class="save-bar">
        <el-button type="primary" icon="el-icon-check" @click="save">保存设置</el-button>
        <el-button icon="el-icon-refresh" @click="load">重置</el-button>
      </div>
    </el-card>
  </div>
</template>

<script>
import { getSettings, saveSettings } from '@/api/oceanbox'

// 后端 sectionMap(deep) 返回带点扁平 key，这里还原为嵌套对象
function unflatten(obj) {
  const out = {}
  Object.keys(obj || {}).forEach(k => {
    const parts = k.split('.')
    let cur = out
    parts.forEach((p, idx) => {
      if (idx === parts.length - 1) {
        cur[p] = obj[k]
      } else {
        cur[p] = cur[p] || {}
        cur = cur[p]
      }
    })
  })
  return out
}

function defaultForm() {
  return {
    cooldownSeconds: 3,
    broadcastBigWin: true,
    broadcastBelowWeight: 15,
    cost: { enabled: true, type: 'points', amount: 100 },
    limits: {
      daily: { enabled: false, amount: 50 },
      weekly: { enabled: false, amount: 200 }
    },
    pityEnabled: false,
    pityAfter: 50,
    pending: { 'expire-after': 0 },
    history: { keep: 50 },
    require: { permission: '', level: 0, item: { material: '', amount: 1 } }
  }
}

export default {
  name: 'Settings',
  data() {
    return { loading: false, tab: 'base', form: defaultForm() }
  },
  created() {
    this.load()
  },
  methods: {
    load() {
      this.loading = true
      getSettings().then(res => {
        const d = res.data || {}
        const f = defaultForm()
        f.cooldownSeconds = d.cooldownSeconds != null ? d.cooldownSeconds : f.cooldownSeconds
        f.broadcastBigWin = !!d.broadcastBigWin
        f.broadcastBelowWeight = d.broadcastBelowWeight != null ? d.broadcastBelowWeight : f.broadcastBelowWeight
        f.cost = Object.assign(f.cost, unflatten(d.cost))
        f.limits = Object.assign(f.limits, unflatten(d.limits))
        f.pityEnabled = !!d.pityEnabled
        f.pityAfter = d.pityAfter || f.pityAfter
        f.pending = Object.assign(f.pending, unflatten(d.pending))
        f.history = Object.assign(f.history, unflatten(d.history))
        f.require = Object.assign(f.require, unflatten(d.require))
        this.form = f
      }).finally(() => { this.loading = false })
    },
    save() {
      saveSettings(this.form).then(res => {
        this.$message.success(res.msg || '设置已保存')
      })
    }
  }
}
</script>

<style scoped>
.tip { margin-left: 10px; font-size: 12px; color: #909399; }
.save-bar { margin-top: 10px; text-align: right; border-top: 1px solid #ebeef5; padding-top: 14px; }
</style>
