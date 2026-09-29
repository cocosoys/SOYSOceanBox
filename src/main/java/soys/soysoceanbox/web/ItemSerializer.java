package soys.soysoceanbox.web;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import soys.soysoceanbox.lottery.RewardDef;
import soys.soysoceanbox.util.NbtHelper;
import soys.soysoceanbox.util.Text;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 物品双向序列化工具（SOYSOceanBox Web 版）。
 *
 * <p>在 Bukkit {@link ItemStack} 与前端 JSON Map 之间转换，并兼容从奖项定义
 * {@link RewardDef} 生成展示数据。字段命名同时对齐 lottery.yml 与前端驼峰习惯：</p>
 * <ul>
 *   <li>{@code material}       材质名（必填，如 DIAMOND）</li>
 *   <li>{@code amount}         数量（ITEM）或金额/点券数值（MONEY/POINTS）</li>
 *   <li>{@code durability}     耐久 / 数据值（1.12.2 方块的 data，如羊毛颜色）</li>
 *   <li>{@code name}           显示名（& 颜色码）</li>
 *   <li>{@code lore}           Lore 列表（& 颜色码）</li>
 *   <li>{@code enchants}       附魔 Map（附魔名 - 等级）</li>
 *   <li>{@code customModelData} 自定义模型数据（1.14+，1.12.2 忽略）</li>
 *   <li>{@code nbt}            原始 NBT（MojangSON，仅保留特殊数据）</li>
 *   <li>{@code command}        控制台指令（COMMAND）</li>
 * </ul>
 */
public final class ItemSerializer {

    private ItemSerializer() {
    }

    // ================================================================
    //  ItemStack -> Map（前端展示 / 背包拷贝）
    // ================================================================

    /**
     * 将单个物品序列化为前端 Map。
     *
     * @return 有序 Map；null 或 AIR 返回 null
     */
    public static Map<String, Object> toMap(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("material", item.getType().name());
        map.put("amount", item.getAmount());
        map.put("durability", (int) item.getDurability());

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (meta.hasDisplayName()) {
                map.put("name", decolor(meta.getDisplayName()));
            }
            if (meta.hasLore()) {
                List<String> lore = new ArrayList<>();
                for (String line : meta.getLore()) {
                    lore.add(decolor(line));
                }
                map.put("lore", lore);
            }
            if (meta.hasEnchants()) {
                Map<String, Integer> enchants = new LinkedHashMap<>();
                for (Map.Entry<Enchantment, Integer> e : meta.getEnchants().entrySet()) {
                    enchants.put(e.getKey().getName(), e.getValue());
                }
                map.put("enchants", enchants);
            }
            Integer cmd = readCustomModelData(meta);
            if (cmd != null) {
                map.put("customModelData", cmd);
            }
        }

        // 特殊 NBT（头颅/药水/旗帜/附魔书等），剥离已由专门字段托管的键
        String nbt = extractNbt(item);
        if (nbt != null) {
            map.put("nbt", nbt);
        }
        return map;
    }

    /**
     * 序列化整个背包（含槽位索引），自动跳过空槽。
     *
     * @return 元素为 {slot, material, amount, ...} 的列表
     */
    public static List<Map<String, Object>> serializeInventory(ItemStack[] contents) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (contents == null) {
            return out;
        }
        for (int slot = 0; slot < contents.length; slot++) {
            Map<String, Object> data = toMap(contents[slot]);
            if (data != null) {
                data.put("slot", slot);
                out.add(data);
            }
        }
        return out;
    }

    // ================================================================
    //  Map -> ItemStack（发放 / 预览）
    // ================================================================

    /**
     * 将前端物品 Map 反序列化为物品。
     *
     * @return 物品；材质缺失/未知返回 null
     */
    @SuppressWarnings("unchecked")
    public static ItemStack toItemStack(Map<?, ?> map) {
        if (map == null) {
            return null;
        }
        Object matObj = map.get("material");
        if (matObj == null || matObj.toString().isEmpty()) {
            return null;
        }
        Material material = Material.matchMaterial(matObj.toString().toUpperCase());
        if (material == null) {
            return null;
        }

        int amount = readInt(map, "amount", 1);
        int durability = readInt(map, "durability", readInt(map, "data", 0));
        ItemStack item = new ItemStack(material, Math.max(1, amount), (short) Math.max(0, durability));

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            Object name = firstNonNull(map, "name", "item-name", "itemName");
            if (name != null) {
                meta.setDisplayName(Text.color(name.toString()));
            }
            Object lore = map.get("lore");
            if (lore instanceof List && !((List<?>) lore).isEmpty()) {
                meta.setLore(Text.color((List<String>) lore));
            }
            applyEnchants(meta, map.get("enchants"));
            Integer cmd = readIntBox(map, "customModelData", "custom-model-data");
            NbtHelper.applyCustomModelData(meta, cmd);
            item.setItemMeta(meta);
        }

        Object nbt = map.get("nbt");
        if (nbt != null && !nbt.toString().trim().isEmpty()) {
            item = NbtHelper.applyNbt(item, nbt.toString());
        }
        return item;
    }

    /**
     * 兼容 Map 或 List 两种附魔写法并写入 meta。
     * <p>Map：{DAMAGE_ALL:2}；List：["DAMAGE_ALL:2"]。</p>
     */
    @SuppressWarnings("unchecked")
    private static void applyEnchants(ItemMeta meta, Object raw) {
        if (raw instanceof Map) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) raw).entrySet()) {
                Enchantment ench = Enchantment.getByName(e.getKey().toString());
                if (ench != null && e.getValue() instanceof Number) {
                    meta.addEnchant(ench, ((Number) e.getValue()).intValue(), true);
                }
            }
        } else if (raw instanceof List) {
            for (Object o : (List<?>) raw) {
                String[] parts = o.toString().split(":");
                Enchantment ench = Enchantment.getByName(parts[0].trim());
                if (ench != null) {
                    int lvl = parts.length > 1 ? safeParseInt(parts[1], 1) : 1;
                    meta.addEnchant(ench, lvl, true);
                }
            }
        }
    }

    // ================================================================
    //  RewardDef -> Map（礼品配置展示）
    // ================================================================

    /**
     * 将奖项定义转为前端 Map（含 id/type/weight/display 及对应载荷字段）。
     */
    public static Map<String, Object> fromRewardDef(RewardDef def) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", def.getId());
        map.put("type", def.getType().name());
        map.put("weight", def.getWeight());
        map.put("display", def.getDisplay());

        switch (def.getType()) {
            case ITEM:
                map.put("material", def.getMaterial());
                map.put("amount", def.getItemAmount());
                map.put("durability", def.getDurability());
                if (def.getItemName() != null) {
                    map.put("name", def.getItemName());
                }
                if (def.getLore() != null) {
                    map.put("lore", def.getLore());
                }
                if (def.getEnchants() != null) {
                    map.put("enchants", def.getEnchants());
                }
                if (def.getCustomModelData() != null) {
                    map.put("customModelData", def.getCustomModelData());
                }
                if (def.getNbt() != null) {
                    map.put("nbt", def.getNbt());
                }
                break;
            case COMMAND:
                map.put("command", def.getCommand());
                break;
            default:
                map.put("amount", def.getValue());
                break;
        }
        return map;
    }

    // ================================================================
    //  NBT 提取（反射 NMS，尽力而为）
    // ================================================================

    /**
     * 提取物品的特殊 NBT（MojangSON）。
     * <p>剥离 display / ench / Damage / CustomModelData 这些已由专门字段管理的键，
     * 只保留头颅、药水、旗帜、附魔书等特殊数据，避免与编辑器字段互相覆盖。</p>
     *
     * @return NBT 字符串；无特殊数据或不支持时返回 null
     */
    public static String extractNbt(ItemStack item) {
        try {
            String ver = serverVersion();
            if (ver == null) {
                return null;
            }
            Class<?> craftItemStack = Class.forName(
                    "org.bukkit.craftbukkit." + ver + ".inventory.CraftItemStack");
            Object nms = craftItemStack.getMethod("asNMSCopy", ItemStack.class).invoke(null, item);
            Object tag = nms.getClass().getMethod("getTag").invoke(nms);
            if (tag == null) {
                return null;
            }
            Class<?> tagClass = tag.getClass();
            for (String key : new String[]{"display", "ench", "Damage", "CustomModelData"}) {
                try {
                    Method remove = tagClass.getMethod("remove", String.class);
                    remove.invoke(tag, key);
                } catch (NoSuchMethodException ignored) {
                    // 该版本无 remove(String)，放弃剥离（极少见）
                    break;
                }
            }
            String s = tag.toString();
            return (s == null || s.equals("{}")) ? null : s;
        } catch (Throwable t) {
            return null;
        }
    }

    // ================================================================
    //  辅助
    // ================================================================

    /** 反射读取 CustomModelData（1.14+；1.12.2 返回 null）。 */
    private static Integer readCustomModelData(ItemMeta meta) {
        try {
            Method getter = meta.getClass().getMethod("getCustomModelData");
            Object v = getter.invoke(meta);
            if (v instanceof Integer && meta.getClass().getMethod("hasCustomModelData").invoke(meta) == Boolean.TRUE) {
                return (Integer) v;
            }
        } catch (Throwable ignored) {
            // 1.12.2 不支持
        }
        return null;
    }

    /** 把 Bukkit 颜色码 § 还原为 &，便于回显编辑。 */
    public static String decolor(String s) {
        return s == null ? "" : s.replace('§', '&');
    }

    private static Object firstNonNull(Map<?, ?> map, String... keys) {
        for (String k : keys) {
            if (map.get(k) != null) {
                return map.get(k);
            }
        }
        return null;
    }

    private static int readInt(Map<?, ?> map, String key, int def) {
        Object v = map.get(key);
        return v == null ? def : safeParseInt(v.toString(), def);
    }

    private static Integer readIntBox(Map<?, ?> map, String... keys) {
        for (String k : keys) {
            Object v = map.get(k);
            if (v != null) {
                try {
                    return Integer.parseInt(v.toString());
                } catch (NumberFormatException ignored) {
                    // 尝试下一个键
                }
            }
        }
        return null;
    }

    private static int safeParseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static String serverVersion() {
        try {
            String pkg = org.bukkit.Bukkit.getServer().getClass().getPackage().getName();
            String[] parts = pkg.split("\\.");
            return parts[parts.length - 1];
        } catch (Throwable t) {
            return null;
        }
    }
}
