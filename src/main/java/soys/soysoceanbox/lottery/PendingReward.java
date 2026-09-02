package soys.soysoceanbox.lottery;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 一次抽奖产出的、尚未领取的奖励。
 * <p>抽奖瞬间即把奖励内容固化下来（金额、物品、指令等），
 * 因此领取时不受后续配置改动影响。可序列化为一段 YAML 节点，
 * 便于同时写入 YAML 文件存储与 SQL 的 TEXT 列。</p>
 */
public class PendingReward {

    /** 奖励类型 */
    public enum Type {
        MONEY,
        POINTS,
        ITEM,
        COMMAND
    }

    private UUID claimId;
    private Type type;
    private String display;

    /** 过期时间戳（毫秒），0 = 永不过期（仍可由配置决定）。 */
    private long expireAt;

    // money / points
    private double value;

    // item
    private String material;
    private int itemAmount;
    private int durability;
    private String itemName;
    private List<String> lore;
    private Map<String, Integer> enchants; // 附魔名 -> 等级

    // item: 1.14+ 自定义模型数据 / 原始 NBT（1.12.2 仅 NBT 尽力而为）
    private Integer customModelData;
    private String nbt;

    // command
    private String command;

    public PendingReward() {
        this.claimId = UUID.randomUUID();
    }

    public UUID getClaimId() {
        return claimId;
    }

    public void setClaimId(UUID claimId) {
        this.claimId = claimId;
    }

    public long getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(long expireAt) {
        this.expireAt = expireAt;
    }

    /**
     * 判断奖励是否已过期（在给定时刻失效）。
     * expireAt 为 0 表示永不过期。
     */
    public boolean isExpired(long nowMillis) {
        return expireAt > 0 && nowMillis >= expireAt;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getDisplay() {
        return display;
    }

    public void setDisplay(String display) {
        this.display = display;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    public String getMaterial() {
        return material;
    }

    public void setMaterial(String material) {
        this.material = material;
    }

    public int getItemAmount() {
        return itemAmount;
    }

    public void setItemAmount(int itemAmount) {
        this.itemAmount = itemAmount;
    }

    public int getDurability() {
        return durability;
    }

    public void setDurability(int durability) {
        this.durability = durability;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public List<String> getLore() {
        return lore;
    }

    public void setLore(List<String> lore) {
        this.lore = lore;
    }

    public Map<String, Integer> getEnchants() {
        return enchants;
    }

    public void setEnchants(Map<String, Integer> enchants) {
        this.enchants = enchants;
    }

    public Integer getCustomModelData() {
        return customModelData;
    }

    public void setCustomModelData(Integer customModelData) {
        this.customModelData = customModelData;
    }

    public String getNbt() {
        return nbt;
    }

    public void setNbt(String nbt) {
        this.nbt = nbt;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    // ================================================================
    //  序列化
    // ================================================================

    public void write(ConfigurationSection section) {
        section.set("claim-id", claimId.toString());
        section.set("expire-at", expireAt);
        section.set("type", type.name());
        section.set("display", display);
        section.set("value", value);
        section.set("material", material);
        section.set("item-amount", itemAmount);
        section.set("durability", durability);
        section.set("item-name", itemName);
        section.set("lore", lore);
        if (enchants != null) {
            section.set("enchants", enchants);
        }
        if (customModelData != null) {
            section.set("custom-model-data", customModelData);
        }
        if (nbt != null) {
            section.set("nbt", nbt);
        }
        section.set("command", command);
    }

    @SuppressWarnings("unchecked")
    public static PendingReward read(ConfigurationSection section) {
        PendingReward reward = new PendingReward();
        String id = section.getString("claim-id");
        if (id != null) {
            try {
                reward.claimId = UUID.fromString(id);
            } catch (IllegalArgumentException ignored) {
                // 无效 id 时保留随机生成的
            }
        }
        reward.expireAt = section.getLong("expire-at", 0);
        try {
            reward.type = Type.valueOf(section.getString("type", "MONEY"));
        } catch (IllegalArgumentException e) {
            reward.type = Type.MONEY;
        }
        reward.display = section.getString("display", "");
        reward.value = section.getDouble("value", 0);
        reward.material = section.getString("material");
        reward.itemAmount = section.getInt("item-amount", 1);
        reward.durability = section.getInt("durability", 0);
        reward.itemName = section.getString("item-name");
        reward.lore = section.getStringList("lore");
        if (section.isConfigurationSection("enchants")) {
            Map<String, Object> raw = section.getConfigurationSection("enchants").getValues(false);
            Map<String, Integer> map = new HashMap<>();
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                if (entry.getValue() instanceof Number) {
                    map.put(entry.getKey(), ((Number) entry.getValue()).intValue());
                }
            }
            reward.enchants = map;
        }
        if (section.contains("custom-model-data")) {
            reward.customModelData = section.getInt("custom-model-data");
        }
        reward.nbt = section.getString("nbt");
        reward.command = section.getString("command");
        return reward;
    }
}
