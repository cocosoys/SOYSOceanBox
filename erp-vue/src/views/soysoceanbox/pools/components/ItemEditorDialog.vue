<template>
  <el-dialog title="物品编辑器" :visible.sync="show" width="680px" append-to-body custom-class="mc-item-editor" @close="onClose">
    <el-form :model="form" label-width="92px" size="small">
      <el-row :gutter="12">
        <el-col :span="8">
          <el-form-item label="奖项ID">
            <el-input v-model="form.id" placeholder="唯一，如 diamond"/>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="类型">
            <el-select v-model="form.type" style="width:100%">
              <el-option label="物品 ITEM" value="ITEM"/>
              <el-option label="指令 COMMAND" value="COMMAND"/>
              <el-option label="金币 MONEY" value="MONEY"/>
              <el-option label="点券 POINTS" value="POINTS"/>
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="权重">
            <el-input-number v-model="form.weight" :min="1" :max="100000" controls-position="right" style="width:100%"/>
          </el-form-item>
        </el-col>
      </el-row>

      <!-- ============ ITEM ============ -->
      <template v-if="form.type === 'ITEM'">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="材质">
              <el-input v-model="form.material" list="mc-mat-list" placeholder="如 DIAMOND_SWORD"/>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="数量">
              <el-input-number v-model="form.amount" :min="1" :max="64" controls-position="right" style="width:100%"/>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="耐久">
              <el-input-number v-model="form.durability" :min="0" :max="32767" controls-position="right" style="width:100%"/>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="自定义名称">
          <el-input v-model="form.name" placeholder="支持 & 颜色码，如 &b&l海洋之刃"/>
        </el-form-item>
        <el-form-item label="说明 Lore">
          <el-input v-model="loreText" type="textarea" :rows="3" placeholder="每行一条，支持 & 颜色码"/>
        </el-form-item>
        <el-form-item label="附魔">
          <div v-for="(en, i) in form.enchants" :key="i" style="display:flex;gap:8px;margin-bottom:6px;">
            <el-select v-model="en.name" filterable allow-create default-first-option style="flex:1">
              <el-option v-for="o in ENCHANT_OPTIONS" :key="o.value" :label="o.label + ' (' + o.value + ')'" :value="o.value"/>
            </el-select>
            <el-input-number v-model="en.level" :min="1" :max="10" controls-position="right" style="width:130px"/>
            <el-button icon="el-icon-delete" circle @click="form.enchants.splice(i, 1)"/>
          </div>
          <el-button size="mini" icon="el-icon-plus" @click="form.enchants.push({ name: 'DAMAGE_ALL', level: 1 })">添加附魔</el-button>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="自定义模型">
              <el-input-number v-model="form.customModelData" :min="0" controls-position="right" style="width:100%"/>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="原始 NBT">
          <el-input v-model="form.nbt" type="textarea" :rows="2" placeholder='SNBT（可选），如 {SkullOwner:{Name:"Notch"}}'/>
        </el-form-item>
      </template>

      <!-- ============ COMMAND ============ -->
      <el-form-item v-else-if="form.type === 'COMMAND'" label="指令">
        <el-input v-model="form.command" placeholder="不带开头 /，支持占位符 %player%"/>
      </el-form-item>

      <!-- ============ MONEY / POINTS ============ -->
      <el-form-item v-else label="数量">
        <el-input-number v-model="form.amount" :min="0" :step="100" controls-position="right"/>
      </el-form-item>

      <el-form-item label="展示名">
        <el-input v-model="form.display" placeholder="可选，缺省按类型自动生成"/>
      </el-form-item>
    </el-form>

    <datalist id="mc-mat-list">
      <option v-for="m in MATERIAL_SUGGEST" :key="m" :value="m"/>
    </datalist>

    <div slot="footer">
      <el-button @click="show = false">取消</el-button>
      <el-button type="primary" @click="confirm">确定</el-button>
    </div>
  </el-dialog>
</template>

<script>
// 1.12.2 常用附魔（Bukkit Enchantment 名 → 中文）
const ENCHANT_OPTIONS = [
  { value: 'DAMAGE_ALL', label: '锋利' },
  { value: 'DAMAGE_UNDEAD', label: '亡灵杀手' },
  { value: 'DAMAGE_ARTHROPODS', label: '节肢杀手' },
  { value: 'KNOCKBACK', label: '击退' },
  { value: 'FIRE_ASPECT', label: '火焰附加' },
  { value: 'LOOT_BONUS_MOBS', label: '抢夺' },
  { value: 'PROTECTION_ENVIRONMENTAL', label: '保护' },
  { value: 'PROTECTION_FIRE', label: '火焰保护' },
  { value: 'PROTECTION_FALL', label: '摔落保护' },
  { value: 'PROTECTION_EXPLOSIONS', label: '爆炸保护' },
  { value: 'PROTECTION_PROJECTILE', label: '弹射物保护' },
  { value: 'THORNS', label: '荆棘' },
  { value: 'DURABILITY', label: '耐久' },
  { value: 'DIG_SPEED', label: '效率' },
  { value: 'SILK_TOUCH', label: '精准采集' },
  { value: 'LOOT_BONUS_BLOCKS', label: '时运' },
  { value: 'ARROW_DAMAGE', label: '力量' },
  { value: 'ARROW_KNOCKBACK', label: '冲击' },
  { value: 'ARROW_FIRE', label: '火矢' },
  { value: 'ARROW_INFINITE', label: '无限' },
  { value: 'OXYGEN', label: '水下呼吸' },
  { value: 'WATER_WORKER', label: '水下速掘' }
]

// 1.12.2 常见材质建议（不含 1.13+ 才有的 NETHERITE_*）
const MATERIAL_SUGGEST = [
  'DIAMOND', 'DIAMOND_BLOCK', 'DIAMOND_SWORD', 'DIAMOND_PICKAXE', 'DIAMOND_AXE',
  'DIAMOND_HELMET', 'DIAMOND_CHESTPLATE', 'DIAMOND_LEGGINGS', 'DIAMOND_BOOTS',
  'EMERALD', 'EMERALD_BLOCK', 'IRON_INGOT', 'IRON_BLOCK', 'GOLD_INGOT', 'GOLD_BLOCK',
  'GOLDEN_APPLE', 'ENCHANTED_GOLDEN_APPLE', 'NETHER_STAR', 'TOTEM_OF_UNDYING',
  'ENDER_PEARL', 'ENDER_EYE', 'EXPERIENCE_BOTTLE', 'OBSIDIAN', 'ENCHANTING_TABLE',
  'CHEST', 'ENDER_CHEST', 'SHULKER_SHELL', 'BOW', 'ARROW', 'SHIELD', 'ELYTRA',
  'ENCHANTED_BOOK', 'BOOK', 'REDSTONE', 'GUNPOWDER', 'BLAZE_ROD', 'MAGMA_CREAM',
  'PRISMARINE_SHARD', 'PRISMARINE_CRYSTALS', 'HEART_OF_THE_SEA', 'NAUTILUS_SHELL',
  'DIAMOND_HORSE_ARMOR', 'GOLDEN_HORSE_ARMOR', 'IRON_HORSE_ARMOR', 'SKULL_ITEM'
]

export default {
  name: 'ItemEditorDialog',
  props: {
    visible: { type: Boolean, default: false },
    value: { type: Object, default: () => ({}) }
  },
  data() {
    return {
      form: this.blank(),
      loreText: '',
      ENCHANT_OPTIONS,
      MATERIAL_SUGGEST
    }
  },
  computed: {
    show: {
      get() { return this.visible },
      set(v) { this.$emit('update:visible', v) }
    }
  },
  watch: {
    value: { immediate: true, deep: true, handler(v) { this.load(v) } }
  },
  methods: {
    blank() {
      return {
        id: '', type: 'ITEM', weight: 1, display: '',
        material: 'DIAMOND', amount: 1, durability: 0, name: '',
        enchants: [], customModelData: 0, nbt: '', command: ''
      }
    },
    load(v) {
      const src = v || {}
      const b = this.blank()
      Object.keys(b).forEach(k => { if (src[k] !== undefined && src[k] !== null) b[k] = src[k] })

      // 附魔兼容：对象 Map / 数组 "NAME:lvl" / 数组对象
      let ench = []
      if (Array.isArray(src.enchants)) {
        ench = src.enchants.map(e => {
          if (typeof e === 'string') {
            const p = e.split(':')
            return { name: p[0], level: parseInt(p[1] || '1', 10) }
          }
          return { name: e.name || e.id, level: parseInt(e.level || e.lvl || '1', 10) }
        })
      } else if (src.enchants && typeof src.enchants === 'object') {
        ench = Object.keys(src.enchants).map(k => ({ name: k, level: parseInt(src.enchants[k], 10) }))
      }
      b.enchants = ench
      this.form = b
      this.loreText = Array.isArray(src.lore) ? src.lore.join('\n') : (src.lore || '')
    },
    confirm() {
      const out = Object.assign({}, this.form)
      out.lore = this.loreText.split('\n')

      // 附魔输出为对象 Map
      const enm = {}
      out.enchants.forEach(e => { if (e.name) enm[e.name] = e.level || 1 })
      out.enchants = enm

      // 清理空的可选项，避免写入无意义节点
      ['name', 'display', 'nbt', 'command'].forEach(k => { if (!out[k]) delete out[k] })
      if (!out.customModelData) delete out.customModelData
      if (Array.isArray(out.lore) && out.lore.join('') === '') delete out.lore
      if (Object.keys(out.enchants).length === 0) delete out.enchants

      this.$emit('confirm', out)
      this.show = false
    },
    onClose() {
      this.$emit('update:visible', false)
    }
  }
}
</script>

<style scoped>
.mc-item-editor >>> .el-dialog__body { padding-top: 10px; }
</style>
