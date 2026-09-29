<template>
  <div class="app-container">
    <!-- 概览卡片 -->
    <el-row :gutter="12" class="overview-row">
      <el-col :xs="12" :sm="8" :md="4" v-for="c in cards" :key="c.key">
        <div class="ov-card">
          <div class="ov-num">{{ c.value }}</div>
          <div class="ov-label">{{ c.label }}</div>
        </div>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <div class="table-toolbar">
        <el-button icon="el-icon-refresh" size="small" @click="loadAll">刷新</el-button>
        <span class="toolbar-tip">默认奖池：{{ overview.defaultPool || '-' }}　存储：{{ overview.primaryStorage || '-' }}</span>
      </div>

      <el-table v-loading="loading" :data="pools" border size="medium" class="pool-table">
        <el-table-column label="礼包（奖池）" min-width="130">
          <template slot-scope="{ row }">
            <el-tag size="medium" effect="plain">{{ row.name }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="170">
          <template slot-scope="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'danger'" size="mini">
              {{ row.enabled ? '已启用' : '已禁用' }}
            </el-tag>
            <el-tag :type="statusMeta(row.status).type" size="mini" style="margin-left:4px;">
              {{ statusMeta(row.status).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="奖项数" prop="rewardsCount" width="80" align="center"/>
        <el-table-column label="生效窗口" min-width="210">
          <template slot-scope="{ row }">
            <span v-if="!row.start && !row.end" class="muted">常驻</span>
            <span v-else>{{ row.start || '即刻' }} ~ {{ row.end || '永久' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="430" fixed="right">
          <template slot-scope="{ row }">
            <el-button size="mini" icon="el-icon-present" @click="openDraw(row)">领取</el-button>
            <el-button size="mini" icon="el-icon-refresh-left" @click="openReset(row)">重置领取</el-button>
            <el-button size="mini" type="primary" icon="el-icon-goods" @click="openGift(row)">礼品配置</el-button>
            <el-button size="mini" icon="el-icon-setting" @click="openConfig(row)">配置</el-button>
            <el-button size="mini" icon="el-icon-document" @click="openRecords(row)">领取记录</el-button>
            <el-button size="mini" :type="row.enabled ? 'info' : 'success'" @click="doToggle(row)">
              {{ row.enabled ? '禁用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- ============ 领取（代抽 / 直接发放） ============ -->
    <el-dialog :title="'领取 · ' + drawPool" :visible.sync="drawVisible" width="520px" append-to-body>
      <el-form :model="drawForm" label-width="92px" size="small">
        <el-form-item label="目标玩家">
          <el-select v-model="drawForm.player" filterable allow-create default-first-option placeholder="选择或输入玩家名" style="width:100%">
            <el-option v-for="p in players" :key="p" :label="p" :value="p"/>
          </el-select>
        </el-form-item>
        <el-form-item label="领取方式">
          <el-radio-group v-model="drawForm.mode">
            <el-radio-button label="draw">代玩家抽奖</el-radio-button>
            <el-radio-button label="give">直接发放奖项</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="drawForm.mode === 'draw'" label="抽奖次数">
          <el-input-number v-model="drawForm.times" :min="1" :max="10" controls-position="right"/>
        </el-form-item>
        <el-form-item v-else label="选择奖项">
          <el-select v-model="drawForm.rewardId" filterable placeholder="选择该奖池中的奖项" style="width:100%">
            <el-option v-for="r in rewardOptions" :key="r.id" :label="(r.display || r.name || r.id)" :value="r.id"/>
          </el-select>
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="drawVisible = false">取消</el-button>
        <el-button type="primary" @click="submitDraw">确定</el-button>
      </div>
    </el-dialog>

    <!-- ============ 重置领取 ============ -->
    <el-dialog :title="'重置领取 · ' + resetPool" :visible.sync="resetVisible" width="520px" append-to-body>
      <el-form :model="resetForm" label-width="92px" size="small">
        <el-form-item label="目标玩家">
          <el-select v-model="resetForm.player" filterable allow-create default-first-option placeholder="选择或输入玩家名" style="width:100%">
            <el-option v-for="p in players" :key="p" :label="p" :value="p"/>
          </el-select>
        </el-form-item>
        <el-form-item label="重置范围">
          <el-checkbox-group v-model="resetForm.scopes">
            <el-checkbox v-for="s in SCOPE_OPTIONS" :key="s.v" :label="s.v">{{ s.l }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="resetVisible = false">取消</el-button>
        <el-button type="warning" @click="submitReset">确定重置</el-button>
      </div>
    </el-dialog>

    <!-- ============ 配置（奖池规则） ============ -->
    <el-dialog :title="'配置 · ' + configPool" :visible.sync="configVisible" width="520px" append-to-body>
      <el-form :model="configForm" label-width="92px" size="small">
        <el-form-item label="启用">
          <el-switch v-model="configForm.enabled" active-text="启用" inactive-text="禁用"/>
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker v-model="configForm.start" type="datetime" value-format="yyyy-MM-dd HH:mm"
            placeholder="留空 = 即刻生效" clearable style="width:100%"/>
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker v-model="configForm.end" type="datetime" value-format="yyyy-MM-dd HH:mm"
            placeholder="留空 = 永久" clearable style="width:100%"/>
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="configVisible = false">取消</el-button>
        <el-button type="primary" @click="submitConfig">保存配置</el-button>
      </div>
    </el-dialog>

    <!-- ============ 领取记录 ============ -->
    <el-dialog :title="'领取记录 · ' + recordsPool" :visible.sync="recordsVisible" width="680px" append-to-body>
      <el-table v-loading="recordsLoading" :data="records" border size="small" max-height="420">
        <el-table-column label="时间" width="168">
          <template slot-scope="{ row }">{{ fmtTime(row.time) }}</template>
        </el-table-column>
        <el-table-column label="玩家" prop="name" width="140"/>
        <el-table-column label="中奖物品" prop="reward"/>
      </el-table>
      <div slot="footer" style="text-align:right;">
        <span class="muted" style="margin-right:12px;">共 {{ recordsTotal }} 条</span>
        <el-button @click="recordsVisible = false">关闭</el-button>
      </div>
    </el-dialog>

    <!-- ============ 礼品配置 ============ -->
    <GiftConfigDialog :visible.sync="giftVisible" :pool="giftPool"/>
  </div>
</template>

<script>
import GiftConfigDialog from './components/GiftConfigDialog.vue'
import {
  adminOverview, adminPools, onlinePlayers,
  getPool, drawFor, giveFor, getPoolConfig, savePoolConfig,
  resetFor, poolRecords, togglePool
} from '@/api/oceanbox'

const SCOPE_OPTIONS = [
  { v: 'all', l: '全部数据' },
  { v: 'pending', l: '待领取奖励' },
  { v: 'pity', l: '保底计数' },
  { v: 'limits', l: '日 / 周次数' },
  { v: 'pool', l: '奖池归属' },
  { v: 'cooldown', l: '冷却时间' }
]

export default {
  name: 'Pools',
  components: { GiftConfigDialog },
  data() {
    return {
      loading: false,
      pools: [],
      overview: {},
      players: [],
      SCOPE_OPTIONS,
      // 礼品配置
      giftVisible: false, giftPool: '',
      // 领取
      drawVisible: false, drawPool: '',
      drawForm: { mode: 'draw', player: '', times: 1, rewardId: '' },
      rewardOptions: [],
      // 重置
      resetVisible: false, resetPool: '',
      resetForm: { player: '', scopes: ['all'] },
      // 配置
      configVisible: false, configPool: '',
      configForm: { enabled: true, start: '', end: '' },
      // 记录
      recordsVisible: false, recordsPool: '',
      records: [], recordsTotal: 0, recordsLoading: false
    }
  },
  computed: {
    cards() {
      const o = this.overview
      return [
        { key: 'online', label: '在线玩家', value: o.onlinePlayers || 0 },
        { key: 'recorded', label: '建档玩家', value: o.recordedPlayers || 0 },
        { key: 'pools', label: '奖池总数', value: o.poolsCount || 0 },
        { key: 'active', label: '生效奖池', value: o.activePools || 0 },
        { key: 'draws', label: '累计抽奖', value: o.totalDraws || 0 },
        { key: 'pending', label: '待领取', value: o.totalPending || 0 }
      ]
    }
  },
  created() {
    this.loadAll()
  },
  methods: {
    loadAll() {
      this.loadOverview()
      this.loadPools()
      this.loadPlayers()
    },
    loadOverview() {
      adminOverview().then(res => { this.overview = res.data || {} })
    },
    loadPools() {
      this.loading = true
      adminPools().then(res => { this.pools = res.data || [] })
        .finally(() => { this.loading = false })
    },
    loadPlayers() {
      onlinePlayers().then(res => { this.players = (res.data || []).map(x => x.name) })
    },

    /* 礼品配置 */
    openGift(row) {
      this.giftPool = row.name
      this.giftVisible = true
    },

    /* 领取 */
    openDraw(row) {
      this.drawPool = row.name
      this.drawForm = { mode: 'draw', player: '', times: 1, rewardId: '' }
      this.rewardOptions = []
      this.loadPlayers()
      getPool(row.name).then(res => {
        this.rewardOptions = (res.data && res.data.rewards) || []
      })
      this.drawVisible = true
    },
    submitDraw() {
      const f = this.drawForm
      if (!f.player) { this.$message.warning('请选择目标玩家'); return }
      if (f.mode === 'draw') {
        drawFor(this.drawPool, f.player, f.times).then(res => {
          this.$notify({ title: '代抽完成', message: res.msg, type: 'success' })
          this.drawVisible = false
          this.loadAll()
        })
      } else {
        if (!f.rewardId) { this.$message.warning('请选择要发放的奖项'); return }
        giveFor(this.drawPool, f.player, { rewardId: f.rewardId }).then(res => {
          this.$notify({ title: '发放完成', message: res.msg, type: 'success' })
          this.drawVisible = false
        })
      }
    },

    /* 重置 */
    openReset(row) {
      this.resetPool = row.name
      this.resetForm = { player: '', scopes: ['all'] }
      this.loadPlayers()
      this.resetVisible = true
    },
    submitReset() {
      const f = this.resetForm
      if (!f.player) { this.$message.warning('请选择目标玩家'); return }
      resetFor(this.resetPool, f.player, f.scopes).then(res => {
        this.$notify({ title: '重置完成', message: res.msg, type: 'warning' })
        this.resetVisible = false
        this.loadAll()
      })
    },

    /* 配置 */
    openConfig(row) {
      this.configPool = row.name
      this.configForm = { enabled: true, start: '', end: '' }
      getPoolConfig(row.name).then(res => {
        const d = res.data || {}
        this.configForm = {
          enabled: d.enabled !== false,
          start: d.start || null,
          end: d.end || null
        }
      })
      this.configVisible = true
    },
    submitConfig() {
      const f = this.configForm
      savePoolConfig(this.configPool, {
        enabled: f.enabled,
        start: f.start || '',
        end: f.end || ''
      }).then(res => {
        this.$message.success(res.msg)
        this.configVisible = false
        this.loadPools()
      })
    },

    /* 记录 */
    openRecords(row) {
      this.recordsPool = row.name
      this.records = []
      this.recordsTotal = 0
      this.recordsLoading = true
      this.recordsVisible = true
      poolRecords(row.name).then(res => {
        const d = res.data || {}
        this.records = d.records || []
        this.recordsTotal = d.total || 0
      }).finally(() => { this.recordsLoading = false })
    },

    /* 启用 / 禁用 */
    doToggle(row) {
      togglePool(row.name).then(res => {
        this.$message.success(res.msg)
        this.loadPools()
      })
    },

    /* 工具 */
    statusMeta(s) {
      return {
        active: { label: '生效中', type: 'success' },
        ended: { label: '已结束', type: 'info' },
        upcoming: { label: '未开始', type: 'warning' }
      }[s] || { label: s, type: 'info' }
    },
    fmtTime(ms) {
      if (!ms) return '-'
      const d = new Date(ms)
      const p = n => (n < 10 ? '0' + n : '' + n)
      return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate())
        + ' ' + p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds())
    }
  }
}
</script>

<style scoped>
.overview-row { margin-bottom: 12px; }
.ov-card {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 14px;
  margin-bottom: 12px;
  text-align: center;
}
.ov-num { font-size: 26px; font-weight: 700; color: #409eff; line-height: 1.2; }
.ov-label { font-size: 12px; color: #909399; margin-top: 4px; }
.table-toolbar { margin-bottom: 10px; display: flex; align-items: center; }
.toolbar-tip { margin-left: 12px; font-size: 12px; color: #909399; }
.muted { color: #909399; }
.pool-table >>> .cell { white-space: normal; line-height: 26px; }
</style>
