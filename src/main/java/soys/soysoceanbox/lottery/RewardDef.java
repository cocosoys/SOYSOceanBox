package soys.soysoceanbox.lottery;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置文件中定义的一个奖项（抽奖池中的一项）。
 * <p>与 {@link PendingReward} 的区别：本类来自配置、可被管理员随时修改；
 * {@link PendingReward} 是抽奖瞬间固化的待领取记录。</p>
 */
public class RewardDef {

    private final String id;
    private final PendingReward.Type type;
    private final int weight;
    private final String display;

    // money / points
    private final double value;

    // item
    private final String material;
    private final int itemAmount;
    private final int durability;
    private final String itemName;
    private final List<String> lore;
    private final Map<String, Integer> enchants;
    private final Integer customModelData;
    private final String nbt;

    // command
    private final String command;

    private RewardDef(Builder b) {
        this.id = b.id;
        this.type = b.type;
        this.weight = b.weight;
        this.display = b.display;
        this.value = b.value;
        this.material = b.material;
        this.itemAmount = b.itemAmount;
        this.durability = b.durability;
        this.itemName = b.itemName;
        this.lore = b.lore;
        this.enchants = b.enchants;
        this.customModelData = b.customModelData;
        this.nbt = b.nbt;
        this.command = b.command;
    }

    public String getId() {
        return id;
    }

    public PendingReward.Type getType() {
        return type;
    }

    public int getWeight() {
        return weight;
    }

    public String getDisplay() {
        return display;
    }

    public double getValue() {
        return value;
    }

    public String getMaterial() {
        return material;
    }

    public int getItemAmount() {
        return itemAmount;
    }

    public int getDurability() {
        return durability;
    }

    public String getItemName() {
        return itemName;
    }

    public List<String> getLore() {
        return lore;
    }

    public Map<String, Integer> getEnchants() {
        return enchants;
    }

    public String getCommand() {
        return command;
    }

    public Integer getCustomModelData() {
        return customModelData;
    }

    public String getNbt() {
        return nbt;
    }

    /**
     * 返回权重替换为 newWeight 的副本（保底池按独立权重抽取时使用）。
     */
    public RewardDef withWeight(int newWeight) {
        Builder b = new Builder(id, type, newWeight)
                .display(display)
                .value(value)
                .material(material)
                .itemAmount(itemAmount)
                .durability(durability)
                .itemName(itemName)
                .lore(lore)
                .enchants(enchants)
                .customModelData(customModelData)
                .nbt(nbt)
                .command(command);
        return b.build();
    }

    /**
     * 将配置定义固化为一条待领取奖励。
     */
    public PendingReward createPending() {
        PendingReward reward = new PendingReward();
        reward.setType(type);
        reward.setDisplay(display);
        reward.setValue(value);
        reward.setMaterial(material);
        reward.setItemAmount(itemAmount);
        reward.setDurability(durability);
        reward.setItemName(itemName);
        reward.setLore(lore);
        reward.setEnchants(enchants);
        reward.setCommand(command);
        reward.setCustomModelData(customModelData);
        reward.setNbt(nbt);
        return reward;
    }

    /**
     * 从配置节点解析一个奖项。id 优先取节点内 id 字段，否则用传入的 fallbackId。
     */
    public static RewardDef fromSection(String fallbackId, ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        Map<String, Object> values = sectionToMap(section);
        if (!values.containsKey("id") && fallbackId != null) {
            values.put("id", fallbackId);
        }
        return fromMap(values);
    }

    /**
     * 从普通 Map（getMapList 的奖项、前端提交的 JSON）解析奖项。
     * 类型非法或权重 <=0 时返回 null。
     */
    public static RewardDef fromMap(Map<?, ?> map) {
        if (map == null) {
            return null;
        }
        Object idObj = map.get("id");
        String id = idObj == null ? null : idObj.toString();

        PendingReward.Type type;
        try {
            type = PendingReward.Type.valueOf(asString(map.get("type"), "MONEY").toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
        int weight = toInt(map.get("weight"), 0);
        if (weight <= 0) {
            return null;
        }
        String display = map.get("display") != null ? map.get("display").toString()
                : (id != null ? id : "");
        Builder b = new Builder(id, type, weight).display(display);

        if (type == PendingReward.Type.ITEM) {
            b.value(0)
                    .material(asString(map.get("material"), null))
                    .itemAmount(toInt(map.get("amount"), 1))
                    .durability(toInt(map.get("durability"), 0))
                    .itemName(asString(map.get("name"), null))
                    .lore(toStringList(map.get("lore")))
                    .enchants(toEnchantMap(map.get("enchants")))
                    .customModelData(firstInt(map, "custom-model-data", "customModelData"))
                    .nbt(asString(map.get("nbt"), null));
        } else if (type == PendingReward.Type.COMMAND) {
            b.value(0).command(asString(map.get("command"), null));
        } else {
            b.value(toDouble(map.get("amount"), 0));
        }
        return b.build();
    }

    /** 把 ConfigurationSection 转为普通 Map（enchants 等子 section 一并展平）。 */
    private static Map<String, Object> sectionToMap(ConfigurationSection section) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : section.getValues(false).entrySet()) {
            Object v = e.getValue();
            if (v instanceof ConfigurationSection) {
                Map<String, Object> sub = new LinkedHashMap<>();
                for (Map.Entry<String, Object> se
                        : ((ConfigurationSection) v).getValues(false).entrySet()) {
                    sub.put(se.getKey(), se.getValue());
                }
                v = sub;
            }
            out.put(e.getKey(), v);
        }
        return out;
    }

    private static int toInt(Object o, int def) {
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        if (o == null) {
            return def;
        }
        try {
            return Integer.parseInt(o.toString().trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static double toDouble(Object o, double def) {
        if (o instanceof Number) {
            return ((Number) o).doubleValue();
        }
        if (o == null) {
            return def;
        }
        try {
            return Double.parseDouble(o.toString().trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static String asString(Object o, String def) {
        return o == null ? def : o.toString();
    }

    private static List<String> toStringList(Object o) {
        List<String> out = new ArrayList<>();
        if (o instanceof List) {
            for (Object e : (List<?>) o) {
                if (e != null) {
                    out.add(e.toString());
                }
            }
        }
        return out;
    }

    /** 附魔 Map（附魔名→等级），兼容 ConfigurationSection / Map / List("NAME:lvl")。 */
    private static Map<String, Integer> toEnchantMap(Object o) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (o instanceof List) {
            for (Object e : (List<?>) o) {
                String[] parts = e.toString().split(":");
                if (parts.length >= 1 && !parts[0].isEmpty()) {
                    out.put(parts[0].trim(), parts.length > 1 ? toInt(parts[1], 1) : 1);
                }
            }
            return out.isEmpty() ? null : out;
        }
        Map<?, ?> source = null;
        if (o instanceof ConfigurationSection) {
            source = ((ConfigurationSection) o).getValues(false);
        } else if (o instanceof Map) {
            source = (Map<?, ?>) o;
        }
        if (source != null) {
            for (Map.Entry<?, ?> e : source.entrySet()) {
                if (e.getValue() instanceof Number) {
                    out.put(e.getKey().toString(), ((Number) e.getValue()).intValue());
                }
            }
        }
        return out.isEmpty() ? null : out;
    }

    private static Integer firstInt(Map<?, ?> map, String... keys) {
        for (String k : keys) {
            Object v = map.get(k);
            if (v != null) {
                int parsed = toInt(v, Integer.MIN_VALUE);
                if (parsed != Integer.MIN_VALUE) {
                    return parsed;
                }
            }
        }
        return null;
    }

    private static class Builder {
        private final String id;
        private final PendingReward.Type type;
        private final int weight;
        private String display = "";
        private double value = 0;
        private String material;
        private int itemAmount = 1;
        private int durability = 0;
        private String itemName;
        private List<String> lore;
        private Map<String, Integer> enchants;
        private Integer customModelData;
        private String nbt;
        private String command;

        Builder(String id, PendingReward.Type type, int weight) {
            this.id = id;
            this.type = type;
            this.weight = weight;
        }

        Builder display(String display) {
            this.display = display;
            return this;
        }

        Builder value(double value) {
            this.value = value;
            return this;
        }

        Builder material(String material) {
            this.material = material;
            return this;
        }

        Builder itemAmount(int itemAmount) {
            this.itemAmount = itemAmount;
            return this;
        }

        Builder durability(int durability) {
            this.durability = durability;
            return this;
        }

        Builder itemName(String itemName) {
            this.itemName = itemName;
            return this;
        }

        Builder lore(List<String> lore) {
            this.lore = lore;
            return this;
        }

        Builder enchants(Map<String, Integer> enchants) {
            this.enchants = enchants;
            return this;
        }

        Builder customModelData(Integer customModelData) {
            this.customModelData = customModelData;
            return this;
        }

        Builder nbt(String nbt) {
            this.nbt = nbt;
            return this;
        }

        Builder command(String command) {
            this.command = command;
            return this;
        }

        RewardDef build() {
            return new RewardDef(this);
        }
    }
}
