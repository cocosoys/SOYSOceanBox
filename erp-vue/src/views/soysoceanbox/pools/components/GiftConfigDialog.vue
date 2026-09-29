<template>
  <el-dialog :title="'礼品配置 · ' + pool" :visible.sync="show" width="900px" append-to-body @open="loadData">
    <el-tabs v-model="tab">
      <!-- ============ 奖池物品栏 ============ -->
      <el-tab-pane label="奖池物品栏" name="pool">
        <div class="inv-bar">
          <div
            v-for="(r, i) in rewards" :key="i"
            class="mc-slot" :class="typeClass(r)"
            :title="slotTitle(r)"
            @click="editItem(i)">
            <span class="slot-label">{{ slotLabel(r) }}</span>
            <span v-if="slotAmount(r) > 1" class="slot-amt">{{ slotAmount(r) }}</span>
            <span class="slot-del" @click.stop="remove(i)">×</span>
          </div>
          <div class="mc-slot add-slot" title="添加物品" @click="addItem">
            <i class="el-icon-plus"/>
          </div>
        </div>
        <div class="bar-hint">点击格子编辑物品（仿 mcmod 物品编辑器），右上角 × 删除；当前共 {{ rewards.length }} 个奖项。</div>
      </el-tab-pane>

      <!-- ============ 读取管理员背包 ============ -->
      <el-tab-pane label="读取我的背包" name="inv">
        <div class="inv-toolbar">
          <el-button size="small" type="primary" icon="el-icon-refresh" @click="readInventory">读取当前管理员背包</el-button>
          <span v-if="operator" class="bar-hint" style="margin-left:10px;">背包来源：{{ operator }}（需该角色在线且为 OP）</span>
        </div>
        <div class="inv-bar">
          <div
            v-for="(it, i) in inventory" :key="i"
            class="mc-slot inv-slot" :class="typeClass(it)"
            :title="'点击拷贝：' + slotTitle(it)"
            @click="copyFromInventory(it)">
            <template v-if="it && it.material">
              <span class="slot-label">{{ slotLabel(it) }}</span>
              <span v-if="slotAmount(it) > 1" class="slot-amt">{{ slotAmount(it) }}</span>
            </template>
          </div>
        </div>
        <div class="bar-hint">点击背包格子，把该物品（含名称 / Lore / 附魔 / NBT）直接拷贝到奖池物品栏。</div>
      </el-tab-pane>
    </el-tabs>

    <ItemEditorDialog :visible.sync="editorVisible" :value="editing" @confirm="onEditorConfirm"/>

    <div slot="footer">
      <el-button @click="show = false">取消</el-button>
      <el-button type="primary" icon="el-icon-check" @click="save">保存礼品配置</el-button>
    </div>
  </el-dialog>
</template>

<script>
import ItemEditorDialog from './ItemEditorDialog.vue'
import { getPool, savePool, getInventory } from '@/api/oceanbox'

export default {
  name: 'GiftConfigDialog',
  components: { ItemEditorDialog },
  props: {
    visible: { type: Boolean, default: false },
    pool: { type: String, default: '' }
  },
  data() {
    return {
      tab: 'pool',
      rewards: [],
      inventory: [],
      operator: '',
      editorVisible: false,
      editing: {},
      editIndex: -1
    }
  },
  computed: {
    show: {
      get() { return this.visible },
      set(v) { this.$emit('update:visible', v) }
    }
  },
  methods: {
    loadData() {
      this.tab = 'pool'
      this.inventory = []
      if (this.pool) {
        getPool(this.pool).then(res => {
          this.rewards = (res.data && res.data.rewards) || []
        })
      }
    },
    addItem() {
      this.editing = { type: 'ITEM', material: 'DIAMOND', amount: 1 }
      this.editIndex = -1
      this.editorVisible = true
    },
    editItem(i) {
      this.editing = Object.assign({}, this.rewards[i])
      this.editIndex = i
      this.editorVisible = true
    },
    onEditorConfirm(out) {
      if (this.editIndex >= 0) {
        this.$set(this.rewards, this.editIndex, out)
      } else {
        this.rewards.push(out)
      }
    },
    remove(i) {
      this.rewards.splice(i, 1)
    },
    readInventory() {
      getInventory().then(res => {
        const d = res.data || {}
        this.operator = d.operator || ''
        this.inventory = (d.items || []).filter(x => x && x.material)
      })
    },
    copyFromInventory(it) {
      const copy = Object.assign({}, it)
      copy.id = 'copy_' + (it.slot != null ? it.slot : 'x') + '_' + Date.now()
      copy.type = copy.type || 'ITEM'
      copy.weight = copy.weight || 1
      this.rewards.push(copy)
      this.$message.success('已拷贝：' + (copy.name ? copy.name : copy.material))
      this.tab = 'pool'
    },
    save() {
      savePool(this.pool, { rewards: this.rewards }).then(res => {
        this.$message.success(res.msg || '已保存礼品配置')
        this.show = false
      })
    },

    /* -------- 格子展示辅助 -------- */
    typeClass(r) {
      return 'type-' + ((r && r.type) || 'ITEM').toLowerCase()
    },
    slotLabel(r) {
      const t = (r && r.type) || 'ITEM'
      if (t === 'COMMAND') return 'CMD'
      if (t === 'MONEY') return '$'
      if (t === 'POINTS') return 'P'
      return (r.material || '?').split('_').map(w => w.charAt(0)).slice(0, 3).join('')
    },
    slotAmount(r) {
      return parseInt(r && r.amount, 10) || 0
    },
    slotTitle(r) {
      if (!r) return ''
      const t = r.type || 'ITEM'
      let s = (r.display || r.name || r.material || t) + '&#10;类型: ' + t + '  权重: ' + (r.weight || 1)
      if (t === 'ITEM') {
        s += '&#10;材质: ' + r.material + ' x' + (r.amount || 1)
        if (r.durability) s += '  耐久: ' + r.durability
      } else if (t === 'COMMAND') {
        s += '&#10;指令: ' + r.command
      } else {
        s += '&#10;数量: ' + r.amount
      }
      return s
    }
  }
}
</script>

<style scoped>
.inv-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  padding: 12px;
  background: #c6c6c6;
  border: 3px solid;
  border-color: #373737 #ffffff #ffffff #373737;
}
.mc-slot {
  width: 54px;
  height: 54px;
  background: #8b8b8b;
  border: 2px solid;
  border-color: #373737 #ffffff #ffffff #373737;
  box-sizing: border-box;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.mc-slot:hover {
  background: #a7d3f0;
}
.slot-label {
  padding: 1px 4px;
  border-radius: 3px;
  font-weight: 700;
  font-size: 13px;
  color: #222;
  background: rgba(255, 255, 255, 0.55);
  line-height: 1.2;
}
.type-command .slot-label { background: #7b46c2; color: #fff; }
.type-money .slot-label { background: #e3a521; color: #3a2c00; }
.type-points .slot-label { background: #23a99a; color: #fff; }
.slot-amt {
  position: absolute;
  right: 2px;
  bottom: 0;
  font-weight: 700;
  font-size: 14px;
  color: #fff;
  text-shadow: 1px 1px 0 #3f3f3f;
}
.slot-del {
  position: absolute;
  top: -1px;
  right: 1px;
  width: 14px;
  height: 14px;
  line-height: 12px;
  text-align: center;
  font-size: 12px;
  color: #fff;
  background: rgba(200, 0, 0, 0.75);
  border-radius: 2px;
  opacity: 0;
}
.mc-slot:hover .slot-del { opacity: 1; }
.add-slot {
  background: #9dca87;
  color: #2f5d1f;
  font-size: 22px;
}
.inv-slot { cursor: copy; }
.bar-hint {
  margin-top: 8px;
  font-size: 12px;
  color: #666;
}
.inv-toolbar { margin-bottom: 4px; }
</style>
