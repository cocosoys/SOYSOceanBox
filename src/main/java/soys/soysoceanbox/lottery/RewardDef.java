package soys.soysoceanbox.lottery;

import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
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
     * 从配置节点解析一个奖项。解析失败时返回 null。
     */
    public static RewardDef fromSection(String id, ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        PendingReward.Type type;
        try {
            type = PendingReward.Type.valueOf(section.getString("type", "MONEY").toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
        int weight = section.getInt("weight", 0);
        if (weight <= 0) {
            return null;
        }

        Builder b = new Builder(id, type, weight)
                .display(section.getString("display", id))
                .value(section.getDouble("amount", 0));

        if (type == PendingReward.Type.ITEM) {
            b.material(section.getString("material"))
                    .itemAmount(section.getInt("amount", 1))
                    .durability(section.getInt("durability", 0))
                    .itemName(section.getString("name"))
                    .lore(section.getStringList("lore"))
                    .enchants(parseEnchants(section.getConfigurationSection("enchants")))
                    .customModelData(section.contains("custom-model-data")
                            ? section.getInt("custom-model-data") : null)
                    .nbt(section.getString("nbt"));
        } else if (type == PendingReward.Type.COMMAND) {
            b.command(section.getString("command"));
        }
        return b.build();
    }

    private static Map<String, Integer> parseEnchants(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        Map<String, Object> raw = section.getValues(false);
        Map<String, Integer> map = new HashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            if (entry.getValue() instanceof Number) {
                map.put(entry.getKey(), ((Number) entry.getValue()).intValue());
            }
        }
        return map.isEmpty() ? null : map;
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
