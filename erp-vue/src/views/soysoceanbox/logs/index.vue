<template>
  <div class="app-container">
    <el-card shadow="never">
      <div slot="header" class="logs-header">
        <el-tabs v-model="tab" style="flex:1;">
          <el-tab-pane label="中奖汇总（业务日志）" name="wins"/>
          <el-tab-pane label="服务器日志" name="server"/>
        </el-tabs>
        <el-button icon="el-icon-refresh" size="small" @click="load">刷新</el-button>
      </div>

      <!-- 中奖汇总 -->
      <el-table v-if="tab === 'wins'" v-loading="loading" :data="wins" border size="small" max-height="560">
        <el-table-column label="时间" width="170">
          <template slot-scope="{ row }">{{ fmtTime(row.time) }}</template>
        </el-table-column>
        <el-table-column label="玩家" prop="name" width="150"/>
        <el-table-column label="奖池" prop="pool" width="120"/>
        <el-table-column label="中奖物品" prop="reward"/>
      </el-table>

      <!-- 服务器日志 -->
      <div v-else v-loading="loading">
        <pre v-if="serverLines.length" class="log-pre">{{ serverLines.join('\n') }}</pre>
        <el-empty v-else description="未读取到与本插件相关的服务器日志行"/>
      </div>
    </el-card>
  </div>
</template>

<script>
import { getLogs } from '@/api/oceanbox'

export default {
  name: 'Logs',
  data() {
    return { loading: false, tab: 'wins', wins: [], serverLines: [] }
  },
  created() {
    this.load()
  },
  methods: {
    load() {
      this.loading = true
      getLogs().then(res => {
        const d = res.data || {}
        this.wins = d.wins || []
        this.serverLines = d.serverLines || []
      }).finally(() => { this.loading = false })
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
.logs-header { display: flex; align-items: center; }
.logs-header >>> .el-tabs__header { margin-bottom: 0; }
.log-pre {
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 12px;
  border-radius: 4px;
  font-family: Consolas, 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.5;
  max-height: 560px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
}
</style>
